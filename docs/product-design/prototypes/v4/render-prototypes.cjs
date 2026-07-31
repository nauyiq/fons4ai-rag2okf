const { chromium } = require("playwright");
const path = require("path");
const { pathToFileURL } = require("url");

const pages = ["login", "register", "library", "documents", "document", "profile", "settings"];
const themes = ["light", "dark"];
const root = __dirname;
const prototypeUrl = pathToFileURL(path.join(root, "rag2okf-v4-prototype.html")).href;

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  });
  const page = await browser.newPage({
    viewport: { width: 1600, height: 1000 },
    deviceScaleFactor: 1,
  });

  for (const pageName of pages) {
    for (const theme of themes) {
      await page.goto(`${prototypeUrl}?page=${pageName}&theme=${theme}`, { waitUntil: "load" });
      await page.screenshot({
        path: path.join(root, `rag2okf-${pageName}-${theme}.png`),
        fullPage: false,
      });
    }
  }

  await browser.close();
})();
