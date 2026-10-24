import { leaseRoute } from "@/lib/lease-route";
export async function POST(request: Request,context:{params:Promise<{id:string}>}) {
  return leaseRoute(request,`/${(await context.params).id}/confirm`,"POST");
}
