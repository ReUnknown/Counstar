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

# 1. InnerTubeX's clients (the app can only use what the library defines).
print("######## INNERTUBEX CLIENTS ########")
try:
    _, jar = get("https://jitpack.io/com/github/MetrolistGroup/innertubex/innertubex/v0.8.4/innertubex-v0.8.4-sources.jar", timeout=120)
    z = zipfile.ZipFile(io.BytesIO(jar))
    for name in z.namelist():
        if name.endswith("YouTubeClient.kt"):
            src = z.read(name).decode()
            for m in re.finditer(r"val (\w+)\s*=\s*\n?\s*YouTubeClient\(([^)]*)\)", src, re.S):
                args = " ".join(m.group(2).split())
                print(" ", m.group(1), "::", args[:260])
except Exception as e:
    print("couldn't read innertubex:", e)

# 2. Live streams: which clients give an HLS manifest whose segments load without a PO token.
print("\n######## LIVE ########")
s = call("search", {"query": "live", "params": "EgJAAQ%3D%3D"})
ids = []
for k, v in walk(s):
    if k == "videoRenderer" and isinstance(v, dict) and v.get("videoId") and v["videoId"] not in ids: ids.append(v["videoId"])
ids = ["jfKfPfyJRdk"] + ids[:2]
print("live ids:", ids)
UA = {
    "IOS": "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
    "ANDROID_VR": "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
    "TVHTML5": "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
    "WEB": "Mozilla/5.0",
    "MWEB": "Mozilla/5.0 (Linux; Android 14) Mobile",
    "WEB_EMBEDDED_PLAYER": "Mozilla/5.0",
}
CLIENTS = [
    {"clientName": "WEB", "clientVersion": "2.20260901.00.00"},
    {"clientName": "MWEB", "clientVersion": "2.20260901.01.00"},
    {"clientName": "WEB_EMBEDDED_PLAYER", "clientVersion": "1.20260901.01.00"},
    {"clientName": "TVHTML5", "clientVersion": "7.20260901.13.00"},
    {"clientName": "IOS", "clientVersion": "20.10.4", "deviceModel": "iPhone16,2", "osName": "iPhone", "osVersion": "18.3.2.22D82"},
    {"clientName": "ANDROID_VR", "clientVersion": "1.62.27", "androidSdkVersion": 32, "deviceModel": "Quest 3", "osName": "Android", "osVersion": "12L"},
]
for vid in ids:
    print(f"\n--- {vid}")
    for c in CLIENTS:
        name = c["clientName"]
        try:
            r = call("player", {"videoId": vid, "contentCheckOk": True, "racyCheckOk": True}, dict(c, hl="en", gl="US"), {"User-Agent": UA[name]})
            st = r.get("playabilityStatus", {})
            sd = r.get("streamingData", {})
            vd = r.get("videoDetails", {})
            hls = sd.get("hlsManifestUrl")
            af = sd.get("adaptiveFormats", [])
            line = f"  {name}: {st.get('status')} {st.get('reason', '')[:60]} isLive={vd.get('isLive')} hls={'yes' if hls else 'no'} dash={'yes' if sd.get('dashManifestUrl') else 'no'} adaptive={len(af)} withUrl={sum(1 for f in af if 'url' in f)}"
            if hls:
                code, m = get(hls, {"User-Agent": UA[name]})
                text = m.decode(errors="replace")
                variants = [l for l in text.splitlines() if l and not l.startswith("#")]
                line += f" | master {code} variants={len(variants)}"
                if variants:
                    code2, pl = get(variants[-1], {"User-Agent": UA[name]})
                    segs = [l for l in pl.decode(errors="replace").splitlines() if l and not l.startswith("#")]
                    line += f" | playlist {code2} segs={len(segs)}"
                    if segs:
                        try:
                            code3, seg = get(segs[-1], {"User-Agent": UA[name]})
                            line += f" | segment {code3} {len(seg)}B"
                        except Exception as e:
                            line += f" | segment FAILED {e}"
            print(line)
        except Exception as e:
            print(f"  {name}: error {e}")

# 3. Search suggestions.
print("\n######## SUGGESTIONS ########")
for url in [
    "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&hl=en&gl=us&q=mrbeas",
    "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=en&q=mrbeas",
]:
    try:
        code, body = get(url)
        print(url.split("?")[0], code, body[:400].decode("utf-8", "replace"))
    except Exception as e:
        print(url.split("?")[0], "error", e)

# 4. A short through /next: does it have the watch page's likes and comments?
print("\n######## SHORT /next ########")
shorts = call("browse", {"browseId": "UCX6OQ3DkcsbYNE6H8uQQuVA", "params": "EgZzaG9ydHPyBgUKA5oBAA%3D%3D"})
sid = None
for k, v in walk(shorts):
    if k == "shortsLockupViewModel" and isinstance(v, dict):
        for kk, vv in walk(v):
            if kk == "videoId": sid = vv; break
    if sid: break
print("short id:", sid)
if sid:
    n = call("next", {"videoId": sid})
    keys = sorted({k for k, _ in walk(n) if k.endswith("Renderer") or k.endswith("ViewModel")})
    print("next renderer keys:", keys[:80])
    print("has videoPrimaryInfoRenderer:", any(k == "videoPrimaryInfoRenderer" for k, _ in walk(n)))
