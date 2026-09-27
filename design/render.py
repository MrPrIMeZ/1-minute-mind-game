import sys, asyncio
from playwright.async_api import async_playwright
async def main(svg, out, size, mask):
    html = f"""<html><body style='margin:0;background:transparent'>
    <div style='width:{size}px;height:{size}px;{"border-radius:22%;overflow:hidden" if mask else ""}'>
    {open(svg).read().replace('width="108" height="108"', f'width="{size}" height="{size}"')}</div></body></html>"""
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/opt/pw-browsers/chromium-1194/chrome-linux/chrome')
        pg = await b.new_page(viewport={'width':size,'height':size})
        await pg.set_content(html); await pg.wait_for_timeout(300)
        await pg.screenshot(path=out, omit_background=True)
        await b.close()
asyncio.run(main(sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]=='1'))
