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

import base64

def seq_for(video_id):
    # protobuf {1: video_id}, the start of the sequenceParams YouTube puts on a Short's card.
    raw = bytes([0x0A, len(video_id)]) + video_id.encode()
    return base64.urlsafe_b64encode(raw).decode()

sid = "aNI2jv7lBPQ"
for label, params in [("built from id", seq_for(sid))]:
    try:
        q = call("reel/reel_watch_sequence", {"sequenceParams": params})
        entries = q.get("entries", [])
        print(f"\n{label}: keys {list(q.keys())} entries {len(entries)}")
        print("  first ids:", [first(e, "videoId") for e in entries[:6]])
        show("  entry 0", entries[0] if entries else None, 30)
        show("  continuation", q.get("continuationEndpoint"), 20)
        tok = first(q.get("continuationEndpoint"), "token") or first(q.get("continuationEndpoint"), "sequenceParams")
        if tok:
            q2 = call("reel/reel_watch_sequence", {"sequenceParams": tok})
            print("  next page entries:", len(q2.get("entries", [])), [first(e, "videoId") for e in q2.get("entries", [])[:4]])
    except Exception as e:
        print(label, "error", e)

for label, body in [("item_watch no id", {"params": "CA8%3D"}), ("FEshorts browse", None)]:
    try:
        r = call("reel/reel_item_watch", body) if body else call("browse", {"browseId": "FEshorts"})
        print(f"\n{label}: keys {list(r.keys())[:12]}")
        print("  replacementEndpoint:", json.dumps(trim(r.get("replacementEndpoint")))[:300])
        print("  any videoId:", first(r, "videoId"))
    except Exception as e:
        print(label, "error", e)
