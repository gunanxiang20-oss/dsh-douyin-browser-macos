// macOS 试验分支：用 Playwright 自带的 WebKit（Safari 引擎）替代 Edge + CDP。
// 语义对齐原来的 CDP 连接：close() 只断开引用，不结束浏览器进程。
import fs from 'node:fs';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { webkit, playwrightPackageDir } from './playwright-runtime.mjs';

const run = promisify(execFile);
let current = null;
let launching = null;
let lastError = null;

export function currentWebKit() { return current?.browser ?? null; }
export function lastWebKitError() { return lastError; }

// 首次安装还没下载 WebKit 引擎时，用插件自带的 playwright 装一次，避免用户看到裸报错。
async function installWebKitEngine() {
  const cli = path.join(playwrightPackageDir, 'cli.js');
  if (!fs.existsSync(cli)) throw new Error('找不到 Playwright CLI，无法自动安装 WebKit 引擎。');
  await run(process.execPath, [cli, 'install', 'webkit'], {
    env: { ...process.env, ELECTRON_RUN_AS_NODE: '1', NODE_OPTIONS: '' },
    timeout: 600_000,
    maxBuffer: 8 * 1024 * 1024,
  });
}

async function openPersistent(profileDir, { retried = false } = {}) {
  try {
    return await webkit.launchPersistentContext(profileDir, { headless: false, viewport: null });
  } catch (error) {
    const message = String(error?.message ?? error);
    if (!retried && /Executable doesn't exist|please run the following command to download new browsers|install webkit/i.test(message)) {
      await installWebKitEngine();
      return openPersistent(profileDir, { retried: true });
    }
    throw error;
  }
}

export async function launchWebKit({ profileDir }) {
  if (current) return current.browser;
  launching ??= (async () => {
    fs.mkdirSync(profileDir, { recursive: true });
    const context = await openPersistent(profileDir);
    const browser = {
      __webkit: true,
      profileDir,
      isConnected: () => current !== null,
      contexts: () => [context],
      close: async () => { current = null; },
    };
    current = { context, browser, startedAt: Date.now() };
    return browser;
  })().catch(error => {
    lastError = String(error?.message ?? error);
    throw error;
  }).finally(() => { launching = null; });
  return launching;
}

export async function disposeWebKit() {
  if (!current) return;
  const { context } = current;
  current = null;
  await context.close().catch(() => {});
}
