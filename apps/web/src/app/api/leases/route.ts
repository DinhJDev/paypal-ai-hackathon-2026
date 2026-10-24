import { leaseRoute } from "@/lib/lease-route";
export async function GET(request: Request) { return leaseRoute(request,"","GET"); }
export async function POST(request: Request) { return leaseRoute(request,"","POST"); }
