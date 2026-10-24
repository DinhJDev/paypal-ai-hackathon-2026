import { leaseRoute } from "@/lib/lease-route";
export async function GET(request: Request,context:{params:Promise<{id:string}>}) {
  return leaseRoute(request,`/${(await context.params).id}`,"GET");
}
