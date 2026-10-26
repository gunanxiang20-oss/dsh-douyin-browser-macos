// macOS 试验分支：用 Playwright 自带的 WebKit（Safari 引擎）替代 Edge + CDP。
// 语义对齐原来的 CDP 连接：close() 只断开引用，不结束浏览器进程。
import fs from 'node:fs';
import { webkit } from './playwright-runtime.mjs';

let current = null;
let launching = null;
let lastError = null;

export function currentWebKit() { return current?.browser ?? null; }
export function lastWebKitError() { return lastError; }

export async function launchWebKit({ profileDir }) {
  if (current) return current.browser;
  launching ??= (async () => {
    fs.mkdirSync(profileDir, { recursive: true });
    const context = await webkit.launchPersistentContext(profileDir, {
      headless: false,
      viewport: null,
    });
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
