package com.suitefolk.api.lease;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.suitefolk.api.identity.OwnerIdentityService;
import com.suitefolk.api.rent.RentScheduleDates;
import com.suitefolk.api.rent.RentScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
public class LeaseWorkspaceService {
    private final LeaseWorkspaceRepository leases;
    private final OwnerIdentityService identities;
    private final RentScheduleService schedules;
    public LeaseWorkspaceService(LeaseWorkspaceRepository leases,OwnerIdentityService identities,RentScheduleService schedules) {
        this.leases=leases;this.identities=identities;this.schedules=schedules;
    }
    private static LocalDate today(){return LocalDate.now(ZoneId.of("America/Chicago"));}
    @Transactional
    public LeaseWorkspaceView create(Jwt jwt,CreateLeaseRequest request) {
        UUID owner=identities.requireOwner(jwt).ownerId();
        leases.createOnce(request.requestId(),owner);
        return view(leases.findByIdAndOwnerId(request.requestId(),owner).orElseThrow(LeaseWorkspaceService::notFound));
    }
    @Transactional(readOnly=true)
    public List<LeaseWorkspaceView> list(Jwt jwt) {
        return leases.findByOwnerIdOrderByUpdatedAtDesc(identities.requireOwner(jwt).ownerId()).stream().map(this::view).toList();
    }
    @Transactional(readOnly=true)
    public LeaseWorkspaceView get(Jwt jwt,UUID id) {
        return view(leases.findByIdAndOwnerId(id,identities.requireOwner(jwt).ownerId()).orElseThrow(LeaseWorkspaceService::notFound));
    }
    @Transactional
    public LeaseWorkspaceView save(Jwt jwt,UUID id,SaveLeaseDraftRequest request) {
        LeaseWorkspace lease=locked(jwt,id);
        if(lease.confirmedAt!=null) throw error(HttpStatus.CONFLICT,"Confirmed lease terms are read-only.");
        LeaseTerms terms=request.terms().normalized();
        // Identical retries recover a committed save whose response was lost; changed terms still conflict.
        if (lease.revision == request.revision()+1 && lease.terms().normalized().equals(terms)) return view(lease);
        requireRevision(lease,request.revision());
        for(LocalDate date:new LocalDate[]{terms.startDate(),terms.endDate(),terms.firstDueDate()})
            if(date!=null && (date.getYear()<1900 || date.getYear()>2100))
                throw error(HttpStatus.BAD_REQUEST,"Lease dates must be between 1900 and 2100.");
        lease.saveTerms(terms);
        leases.flush();
        return view(lease);
    }
    @Transactional
    public LeaseWorkspaceView confirm(Jwt jwt,UUID id,ConfirmLeaseRequest request) {
        LeaseWorkspace lease=locked(jwt,id);
        requireRevision(lease,request.revision());
        if(lease.confirmedAt!=null) return view(lease);
        if(!issues(lease.terms()).isEmpty()) throw error(HttpStatus.BAD_REQUEST,"Complete the lease terms and review the dates before confirming.");
        UUID schedule=schedules.createForLease(lease.ownerId,lease.suiteLabel,lease.amount,lease.frequency,lease.firstDueDate,lease.endDate);
        lease.confirm(lease.ownerId,schedule);
        leases.flush();
        return view(lease);
    }
    private LeaseWorkspace locked(Jwt jwt,UUID id) {
        return leases.lockOwned(id,identities.requireOwner(jwt).ownerId()).orElseThrow(LeaseWorkspaceService::notFound);
    }
    private void requireRevision(LeaseWorkspace lease,long revision) {
        if(lease.revision!=revision) throw error(HttpStatus.CONFLICT,"The saved lease changed. Reload it before saving or confirming.");
    }
    private List<String> issues(LeaseTerms terms) {
        List<String> result=new ArrayList<>();
        if(terms.tenantName().isBlank()) result.add("Enter the tenant name.");
        if(terms.suiteLabel().isBlank()) result.add("Enter the suite label.");
        if(terms.amount()==null) result.add("Enter the rent amount.");
        if(terms.frequency()==null) result.add("Choose weekly or monthly rent.");
        if(terms.startDate()==null) result.add("Enter the lease start date.");
        if(terms.endDate()==null) result.add("Enter the lease end date.");
        if(terms.firstDueDate()==null) result.add("Enter the first rent due date.");
        if(terms.startDate()!=null && terms.endDate()!=null && terms.endDate().isBefore(terms.startDate()))
            result.add("Lease end must be on or after lease start.");
        if(terms.firstDueDate()!=null) {
            if(terms.firstDueDate().isBefore(today())) result.add("Initial rent scheduling must begin today or later.");
            if(terms.startDate()!=null && terms.firstDueDate().isBefore(terms.startDate()) ||
                terms.endDate()!=null && terms.firstDueDate().isAfter(terms.endDate()))
                result.add("The first due date must fall within the lease term.");
        }
        return result;
    }
    private LeaseWorkspaceView view(LeaseWorkspace lease) {
        boolean confirmed=lease.confirmedAt!=null;
        List<String> problems=confirmed ? List.of() : issues(lease.terms());
        List<LocalDate> preview=new ArrayList<>();
        boolean more=false;
        if(problems.isEmpty()) {
            LocalDate next=lease.firstDueDate;
            while(!next.isAfter(lease.endDate) && preview.size()<5) {
                preview.add(next);
                next=RentScheduleDates.nextDueDate(lease.frequency,next,lease.firstDueDate.getDayOfMonth());
            }
            more=!next.isAfter(lease.endDate);
        }
        String lifecycle=!confirmed ? "UNCONFIRMED" : today().isBefore(lease.startDate) ? "UPCOMING" :
            today().isAfter(lease.endDate) ? "ENDED" : "ACTIVE";
        return new LeaseWorkspaceView(lease.id,confirmed ? "CONFIRMED" : "WORKING",lease.revision,lease.terms(),lifecycle,
            problems,preview,more,lease.scheduleId,lease.updatedAt,lease.confirmedAt);
    }
    private static ErrorResponseException notFound(){return error(HttpStatus.NOT_FOUND,"Lease workspace was not found.");}
    private static ErrorResponseException error(HttpStatus status,String detail){
        return new ErrorResponseException(status,ProblemDetail.forStatusAndDetail(status,detail),null);
    }
}
