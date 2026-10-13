# Development probe: fetches a few public YouTube responses (signed out) and prints their shape,
# so parsers can be written against real data. Runs on GitHub's machines, which can reach YouTube.
import json, sys, urllib.request

CLIENT = {"clientName": "WEB", "clientVersion": "2.20260901.00.00", "hl": "en", "gl": "US"}

def call(endpoint, body):
    body = dict(body, context={"client": CLIENT})
    req = urllib.request.Request(
        f"https://www.youtube.com/youtubei/v1/{endpoint}?prettyPrint=false",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "User-Agent": "Mozilla/5.0", "Accept-Language": "en-US"},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)

def trim(o, depth=0, max_depth=14):
    if depth > max_depth: return "…"
    if isinstance(o, dict):
        return {k: trim(v, depth + 1, max_depth) for k, v in o.items() if k not in ("trackingParams", "clickTrackingParams", "loggingDirectives", "accessibility", "accessibilityData", "commandMetadata", "webCommandMetadata")}
    if isinstance(o, list):
        out = [trim(v, depth + 1, max_depth) for v in o[:4]]
        if len(o) > 4: out.append(f"… +{len(o) - 4} more")
        return out
    if isinstance(o, str) and len(o) > 140: return o[:140] + "…"
    return o

def walk(o, path=""):
    if isinstance(o, dict):
        for k, v in o.items():
            yield k, v, path
            yield from walk(v, path + "/" + k)
    elif isinstance(o, list):
        for v in o: yield from walk(v, path)

def first(o, key):
    for k, v, _ in walk(o):
        if k == key: return v
    return None

def show(title, o, limit=160):
    print(f"\n===== {title} =====")
    lines = json.dumps(trim(o), indent=1).splitlines()
    print("\n".join(lines[:limit]))
    if len(lines) > limit: print(f"… ({len(lines) - limit} more lines)")

def keys_like(o, *words):
    seen = {}
    for k, v, p in walk(o):
        if any(w in k for w in words) and k not in seen: seen[k] = p
    return seen

def tabs(root):
    out = []
    for t in (first(root, "twoColumnBrowseResultsRenderer") or {}).get("tabs", []):
        tr = t.get("tabRenderer") or t.get("expandableTabRenderer") or {}
        out.append((tr.get("title"), (tr.get("endpoint") or {}).get("browseEndpoint", {}).get("params")))
    return out

def channel(cid, name):
    root = call("browse", {"browseId": cid})
    print(f"\n######## CHANNEL {name} ########")
    print("tabs:", tabs(root))
    show("banner", first(root.get("header"), "banner"), 40)
    return root

mr = channel("UCX6OQ3DkcsbYNE6H8uQQuVA", "MrBeast")
show("header keys", keys_like(mr.get("header"), "anner", "Banner"), 30)

videos_params = dict(tabs(mr)).get("Videos")
if videos_params:
    v = call("browse", {"browseId": "UCX6OQ3DkcsbYNE6H8uQQuVA", "params": videos_params})
    print("\nvideos tab chip-ish keys:", keys_like(v, "chip", "Chip", "sort", "Sort", "filter", "Filter"))
    show("videos chip bar", first(v, "chipBarViewModel") or first(v, "feedFilterChipBarRenderer") or first(v, "sortFilterSubMenuRenderer"), 120)

for cid, name in [("UCX6OQ3DkcsbYNE6H8uQQuVA", "MrBeast"), ("UCBJycsmduvYEL83R_U4JriQ", "MKBHD"), ("UCXuqSBlHAE6Xw-yeJA0Tunw", "LTT")]:
    root = mr if name == "MrBeast" else call("browse", {"browseId": cid})
    p = dict(tabs(root)).get("Posts") or dict(tabs(root)).get("Community")
    if not p:
        print(f"\n{name}: no Posts tab"); continue
    posts = call("browse", {"browseId": cid, "params": p})
    print(f"\n######## POSTS {name} ########")
    print("post-ish keys:", keys_like(posts, "post", "Post", "backstage", "Backstage", "poll", "Poll", "quiz", "Quiz"))
    kinds = {}
    for k, v, _ in walk(posts):
        if k == "backstageAttachment" and isinstance(v, dict):
            for kk in v: kinds.setdefault(kk, v[kk])
    print("attachment kinds:", list(kinds))
    if name == "MrBeast":
        show("first post thread", first(posts, "backstagePostThreadRenderer") or first(posts, "postRenderer"), 260)
    for kk, vv in kinds.items():
        if kk != "videoRenderer": show(f"{name} attachment {kk}", vv, 120)
    detail = None
    for k, v, _ in walk(posts):
        if k == "browseEndpoint" and isinstance(v, dict) and v.get("browseId") == "FEpost_detail":
            detail = v; break
    if detail and name == "MrBeast":
        d = call("browse", {"browseId": "FEpost_detail", "params": detail.get("params")})
        print("\npost detail keys:", keys_like(d, "comment", "Comment", "continuation"))
        show("post detail section ids", [s.get("itemSectionRenderer", {}).get("sectionIdentifier") for s in (first(d, "sectionListRenderer") or {}).get("contents", [])], 20)

s = call("search", {"query": "mrbeast"})
print("\n######## SEARCH mrbeast ########")
contents = (first(s, "sectionListRenderer") or {}).get("contents", [])
for sec in contents:
    for item in sec.get("itemSectionRenderer", {}).get("contents", []):
        k = next(iter(item))
        extra = ""
        if k == "channelRenderer": extra = item[k].get("title", {}).get("simpleText", "")
        if k == "shelfRenderer": extra = json.dumps(trim(item[k].get("title")))[:80] + " " + str(list(first(item[k], "items") and [next(iter(x)) for x in first(item[k], "items")][:3] or []))
        print(" ", k, extra)
