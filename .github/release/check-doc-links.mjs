import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, resolve } from 'node:path';
import MarkdownIt from 'markdown-it';

const markdown = new MarkdownIt();
const files = execFileSync('git', ['ls-files', '-co', '--exclude-standard', '-z', '--', '*.md'], { encoding: 'utf8' })
  .split('\0').filter(file => file && existsSync(file));
const anchors = new Map();
const links = [];
for (const file of files) {
  const tokens = markdown.parse(readFileSync(file, 'utf8'), {});
  const headings = new Set();
  let line = 1;
  for (const [index, token] of tokens.entries()) {
    if (token.map) line = token.map[0] + 1;
    if (token.type !== 'inline') continue;
    if (tokens[index - 1]?.type === 'heading_open') {
      const title = token.children.filter(child => ['text', 'code_inline'].includes(child.type))
        .map(child => child.content).join('');
      const base = title.toLowerCase().replace(/[^\p{L}\p{N}\p{M}_\s-]/gu, '').replace(/ /g, '-');
      let slug = base;
      for (let suffix = 1; headings.has(slug); suffix++) slug = base + '-' + suffix;
      headings.add(slug);
    }
    for (const child of token.children) {
      if (child.type === 'link_open') {
        links.push({ file, line, href: child.attrGet('href') });
      }
    }
  }
  anchors.set(resolve(file), headings);
}

let checked = 0;
for (const { file, line, href } of links) {
  // Modrinth needs public GitHub URLs. Check their destination in the proposed tree before it is published.
  const repositoryLink = /^https:\/\/github\.com\/gitEpildev\/TeleHop\/(?:blob|tree)\/main\/(.+)$/i.exec(href);
  if (!repositoryLink && /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(href)) continue;
  const [path, fragment] = (repositoryLink?.[1] ?? href).split('#', 2);
  const directory = repositoryLink ? '.' : dirname(file);
  const target = path ? resolve(directory, decodeURIComponent(path)) : resolve(file);
  checked++;
  if (!existsSync(target) || (fragment && statSync(target).isFile() && extname(target) === '.md'
      && !anchors.get(target)?.has(decodeURIComponent(fragment)))) {
    console.error(`${file}:${line}: unresolved local link ${href}`);
    process.exitCode = 1;
  }
}
if (!process.exitCode) console.log(`Verified ${checked} repository links and headings across ${files.length} Markdown files.`);
