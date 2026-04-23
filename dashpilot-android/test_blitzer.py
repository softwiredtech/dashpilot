import aiohttp
import asyncio
import math
from typing import List, Dict, Any

async def fetch_blitzers(
    latitude: float,
    longitude: float,
    radius_km: float = 10.0,
    types: List[int] = None
) -> List[Dict[str, Any]]:
    """
    Fetch current blitzers (speed traps) from the public Atudo API used by Blitzer.de
    """
    if types is None:
        # Default: mobile + trailer (most useful ones)
        types = [0,1,2,3,4,5,6, "ts",
                 101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,117]

    # Convert types to string for the API
    type_str = ",".join(map(str, types))

    # Simple bounding box calculation (approximate, good enough for quick testing)
    lat_delta = radius_km / 111.0
    lng_delta = radius_km / (111.0 * math.cos(math.radians(latitude)))

    low_lat = latitude - lat_delta
    high_lat = latitude + lat_delta
    low_lng = longitude - lng_delta
    high_lng = longitude + lng_delta

    url = (
        f"https://cdn2.atudo.net/api/4.0/pois.php"
        f"?type={type_str}"
        f"&box={low_lat:.6f},{low_lng:.6f},{high_lat:.6f},{high_lng:.6f}"
        f"&z=18"
    )

    print(f"Fetching from: {url}")

    async with aiohttp.ClientSession() as session:
        async with session.get(url) as resp:
            if resp.status != 200:
                print(f"Error: HTTP {resp.status}")
                return []
            data = await resp.json()
            pois = data.get("pois", [])
            # Filter out cluster objects — they aren't actual cameras
            return [p for p in pois if p.get("type") != "cluster"]


async def main():
    # === CHANGE THESE VALUES ===
    LAT = 46.0727      # Pécs, Hungary
    LON = 18.2323
    RADIUS_KM = 10.0   # Search radius in km

    pois = await fetch_blitzers(LAT, LON, RADIUS_KM)

    print(f"\nFound {len(pois)} blitzer(s) in the area:\n")

    for i, poi in enumerate(pois[:20], 1):   # limit output to first 20
        info = poi.get("info", {})
        print(f"{i:2d}. {poi.get('address', 'No address')}")
        print(f"    Lat: {poi.get('lat')} | Lng: {poi.get('lng')}")
        print(f"    Type: {poi.get('type')} | Confirmed: {info.get('confirmed')} | "
              f"Vmax: {poi.get('vmax', 'N/A')} km/h")
        print(f"    Link: https://map.blitzer.de/v5/ID/{poi.get('backend')}/")
        print("-" * 60)

    if len(pois) > 20:
        print(f"... and {len(pois)-20} more (total: {len(pois)})")


if __name__ == "__main__":
    asyncio.run(main())