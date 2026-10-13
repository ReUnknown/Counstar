# Development probe: fetches a few public YouTube responses (signed out) and prints their shape,
# so parsers can be written against real data. Runs on GitHub's machines, which can reach YouTube.
import io, json, re, urllib.parse, urllib.request, zipfile

def get(url, headers=None, data=None, timeout=30):
    req = urllib.request.Request(url, data=data, headers=dict({"User-Agent": "Mozilla/5.0", "Accept-Language": "en-US"}, **(headers or {})))
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read()

def call(endpoint, body, client=None, headers=None):
    client = client or {"clientName": "WEB", "clientVersion": "2.20260901.00.00", "hl": "en", "gl": "US"}
    body = dict(body, context={"client": client})
    _, data = get(f"https://www.youtube.com/youtubei/v1/{endpoint}?prettyPrint=false", dict({"Content-Type": "application/json"}, **(headers or {})), json.dumps(body).encode())
    return json.loads(data)

def walk(o):
    if isinstance(o, dict):
        for k, v in o.items():
            yield k, v
            yield from walk(v)
    elif isinstance(o, list):
        for v in o: yield from walk(v)


def trim(o, depth=0, max_depth=12):
    if depth > max_depth: return "..."
    if isinstance(o, dict):
        return {k: trim(v, depth + 1, max_depth) for k, v in o.items() if k not in ("trackingParams", "clickTrackingParams", "loggingDirectives", "accessibility", "accessibilityData", "commandMetadata", "webCommandMetadata", "rendererContext")}
    if isinstance(o, list):
        out = [trim(v, depth + 1, max_depth) for v in o[:3]]
        if len(o) > 3: out.append(f"... +{len(o) - 3} more")
        return out
    if isinstance(o, str) and len(o) > 120: return o[:120] + "..."
    return o

def show(title, o, limit=120):
    print(f"\n===== {title} =====")
    lines = json.dumps(trim(o), indent=1).splitlines()
    print("\n".join(lines[:limit]))
    if len(lines) > limit: print(f"... ({len(lines) - limit} more lines)")

def first(o, key):
    for k, v in walk(o):
        if k == key: return v
    return None

# A. A short through /next: the owner (name, picture).
shorts = call("browse", {"browseId": "UCX6OQ3DkcsbYNE6H8uQQuVA", "params": "EgZzaG9ydHPyBgUKA5oBAA%3D%3D"})
show("channel shorts tab lockup", first(shorts, "shortsLockupViewModel"), 90)
sid = None
for k, v in walk(shorts):
    if k == "reelWatchEndpoint" and isinstance(v, dict) and v.get("videoId"): sid = v["videoId"]; break
print("short id:", sid)
n = call("next", {"videoId": sid})
show("short next videoOwnerRenderer", first(n, "videoOwnerRenderer"), 60)

# B. A run of shorts to swipe through.
w = call("reel/reel_item_watch", {"playerRequest": {"videoId": sid}, "params": "CA8%3D", "disablePlayerResponse": True})
print("\nreel_item_watch keys:", list(w.keys()))
seq = w.get("sequenceContinuation")
print("sequenceContinuation:", (seq or "")[:80])
show("reel_item_watch overlay", w.get("overlay"), 50)
if seq:
    q = call("reel/reel_watch_sequence", {"sequenceParams": seq})
    print("\nreel_watch_sequence keys:", list(q.keys()), "entries:", len(q.get("entries", [])))
    show("first entry", (q.get("entries") or [None])[0], 40)
    print("continuation:", str(q.get("continuationEndpoint") or q.get("continuation"))[:200])
# Without a seed: what the Shorts tab would start from.
try:
    q0 = call("reel/reel_watch_sequence", {"sequenceParams": "CA8%3D"})
    print("\nunseeded sequence entries:", len(q0.get("entries", [])))
except Exception as e:
    print("unseeded sequence error:", e)

# C. Shorts in search results and home-like shelves.
s = call("search", {"query": "mrbeast"})
show("search shorts shelf (gridShelfViewModel)", first(s, "gridShelfViewModel"), 80)
s2 = call("search", {"query": "funny cats"})
print("\nfunny cats has reelShelfRenderer:", first(s2, "reelShelfRenderer") is not None, " shortsLockupViewModel:", first(s2, "shortsLockupViewModel") is not None)

# D. Audio tracks (dubbed videos).
for name, client, ua in [
    ("ANDROID_VR", {"clientName": "ANDROID_VR", "clientVersion": "1.65.10", "androidSdkVersion": 32, "osName": "Android", "osVersion": "12L"}, "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"),
    ("IOS", {"clientName": "IOS", "clientVersion": "21.26.4", "deviceModel": "iPhone16,2", "osName": "iPhone", "osVersion": "18.3.2.22D82"}, "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"),
]:
    try:
        r = call("player", {"videoId": "0e3GPea1Tyg", "contentCheckOk": True, "racyCheckOk": True}, dict(client, hl="en", gl="US"), {"User-Agent": ua})
        st = r.get("playabilityStatus", {})
        tracks = {}
        for f in r.get("streamingData", {}).get("adaptiveFormats", []):
            if "audioTrack" in f: tracks[f["audioTrack"].get("id")] = f["audioTrack"]
        print(f"\n{name}: {st.get('status')} {st.get('reason','')[:50]} audio tracks: {len(tracks)}")
        for t in list(tracks.values())[:3]: print("  ", t)
    except Exception as e:
        print(name, "error", e)
