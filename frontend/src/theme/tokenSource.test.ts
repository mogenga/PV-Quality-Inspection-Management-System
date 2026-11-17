/// <reference types="node" />
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { color } from './tokens';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '../../..');
const localCssPath = path.join(repoRoot, 'frontend/src/styles/tokens.css');
const designCssPath = path.join(repoRoot, 'design/aesthetic/tokens.css');

// 颜色行形如：  --accent: oklch(0.546 0.245 262); /* #2563eb */
const TOKEN_LINE = /^\s*(--[\w-]+):[^;]*;\s*\/\*\s*(#[0-9a-fA-F]{6})\s*\*\/\s*$/gm;

const toKey = (cssVar: string) =>
  cssVar.replace(/^--/, '').replace(/-([a-z0-9])/g, (_, ch: string) => ch.toUpperCase());

const parseHexTokens = (css: string) =>
  new Map(Array.from(css.matchAll(TOKEN_LINE), (m) => [toKey(m[1]), m[2]] as const));

describe('设计令牌源码闸门', () => {
  it('前端令牌副本与 design/aesthetic/tokens.css 字节级一致', () => {
    const local = readFileSync(localCssPath);
    const source = readFileSync(designCssPath);
    expect(local.equals(source)).toBe(true);
  });

  it('tokens.ts 的 16 个颜色常量逐个等于 tokens.css 注释里的 hex', () => {
    const parsed = parseHexTokens(readFileSync(localCssPath, 'utf8'));
    expect(parsed.size).toBe(16);
    expect([...parsed.keys()].sort()).toEqual(Object.keys(color).sort());
    const colorMap = color as Record<string, string>;
    for (const [key, hex] of parsed) {
      expect(colorMap[key], `color.${key} 应等于 ${hex}`).toBe(hex.toUpperCase());
    }
  });
});
