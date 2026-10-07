from pathlib import Path
import argparse
import re

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(
    description="Extract the V20 Blogger source into the Android WebView asset.",
)
parser.add_argument(
    "--source",
    type=Path,
    default=Path("V20.xml"),
    help="Source XML path, relative to this script's directory unless absolute.",
)
parser.add_argument(
    "--output",
    type=Path,
    default=Path("app/src/main/assets/v20/index.html"),
    help="Output HTML path, relative to this script's directory unless absolute.",
)
parser.add_argument(
    "--check",
    action="store_true",
    help="Verify the bundled HTML matches the generated output without writing it.",
)
args = parser.parse_args()
source_path = args.source if args.source.is_absolute() else ROOT / args.source
output_path = args.output if args.output.is_absolute() else ROOT / args.output

src = source_path.read_text(encoding="utf-8")

# CSS from Blogger skin + all regular style blocks.
bskin_m = re.search(r"<b:skin><!\[CDATA\[(.*?)\]\]></b:skin>", src, re.S)
if not bskin_m:
    raise SystemExit('b:skin not found')
css_parts = [bskin_m.group(1)]
for m in re.finditer(r"<style\b[^>]*>(.*?)</style>", src, re.S | re.I):
    css = m.group(1)
    # Skip the b:skin duplicate isn't matched here; keep every regular style block.
    css_parts.append(css)
css = "\n\n".join(css_parts)

# Main body UI, excluding Blogger-specific widget sections after the app markup.
body_start = src.index('<body>') + len('<body>')
marker = "  <!-- Native Blogger stats + widgets preserved verbatim. -->"
body_end = src.index(marker)
body = src[body_start:body_end]

# Remove Blogger ad-push inline scripts from the standalone build. Keep ad placeholders as UI spacers.
body = re.sub(r"<script>\s*//<!\[CDATA\[\s*\(adsbygoogle\s*=\s*window\.adsbygoogle.*?//\]\]>\s*</script>", "", body, flags=re.S)
# Avoid navigation out of the bundled app when the brand is clicked.
body = body.replace("href='/'", "href='#home'")

# Main application JS: the script containing CONFIG/db/window.SAMI_APP.
script_blocks = []
for m in re.finditer(r"<script\b[^>]*>(.*?)</script>", src, re.S | re.I):
    content = m.group(1)
    if 'const CONFIG' in content and 'window.SAMI_APP' in content:
        script_blocks.append(content)
if not script_blocks:
    raise SystemExit('main application script not found')
main_js = script_blocks[-1]
# Remove CDATA wrappers, harmless but cleaner in HTML.
main_js = main_js.replace('//<![CDATA[', '').replace('//]]>', '')

# Final mobile-mode script.
mobile_candidates = []
for m in re.finditer(r"<script\b[^>]*>(.*?)</script>", src, re.S | re.I):
    content = m.group(1)
    if "sami_mm" in content:
        mobile_candidates.append(content)
if not mobile_candidates:
    raise SystemExit("mobile-mode script not found")
mobile_js = mobile_candidates[-1].replace("//<![CDATA[", "").replace("//]]>", "")

head = f'''<!doctype html>\n<html lang="ar" dir="rtl">\n<head>\n<meta charset="UTF-8">\n<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">\n<meta name="theme-color" content="#050b12">\n<meta name="mobile-web-app-capable" content="yes">\n<meta name="apple-mobile-web-app-capable" content="yes">\n<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">\n<title>SAMI Live TV</title>\n<link rel="preconnect" href="https://fonts.googleapis.com">\n<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>\n<link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;700;900&family=Readex+Pro:wght@400;600;700&family=IBM+Plex+Sans+Arabic:wght@300;400;500;600&display=swap" rel="stylesheet">\n<style>\n{css}\n\n/* Standalone APK adjustments */\nhtml,body{{min-height:100%;}}\nbody{{margin:0;}}\n</style>\n</head>\n<body>\n{body}\n<script>\n{main_js}\n</script>\n<script>\n{mobile_js}\n</script>\n</body>\n</html>\n'''

# Minimal local-app compatibility shims: prevent Google AdSense from throwing and mark the page as standalone.
head = head.replace('<script>\n' + main_js, '<script>\nwindow.google = window.google || {}; window.adsbygoogle = window.adsbygoogle || [];\n' + main_js, 1)

out = output_path
if args.check:
    if not out.is_file():
        raise SystemExit(f"Bundled V20 asset is missing: {out}")
    if out.read_text(encoding="utf-8") != head:
        raise SystemExit(
            f"Bundled V20 asset is out of date. Run: python3 {Path(__file__).name}",
        )
    print("verified", out, "bytes=", out.stat().st_size)
else:
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(head, encoding="utf-8")
    print("wrote", out, "bytes=", out.stat().st_size)
print('body bytes=', len(body.encode()), 'css bytes=', len(css.encode()), 'main js bytes=', len(main_js.encode()), 'mobile js bytes=', len(mobile_js.encode()))
print('function count=', len(re.findall(r'function\s+[A-Za-z0-9_]+', main_js)))
