// Read Markdown from stdin. Report spelling in visible text and describe release sections.
import { readFileSync } from 'node:fs';
import dictionary from 'dictionary-en-gb';
import MarkdownIt from 'markdown-it';
import nspell from 'nspell';

const input = readFileSync(0, 'utf8');
const spell = nspell(dictionary);
const markdown = new MarkdownIt({ html: false, linkify: true });
const terms = new Set(readFileSync(new URL('project-words.txt', import.meta.url), 'utf8').trim().split(/\s+/));
const issues = [];
const headings = [];
const bullets = [];
const paragraphs = [];
const vocabulary = new Map();
const tokens = markdown.parse(input, {});
let listDepth = 0;
let quoteDepth = 0;

for (const [index, token] of tokens.entries()) {
  if (token.type === 'blockquote_open') quoteDepth++;
  if (token.type === 'blockquote_close') quoteDepth--;
  if (token.type === 'list_item_open') listDepth++;
  if (token.type === 'list_item_close') listDepth--;
  if (token.type !== 'inline') continue;
  let line = token.map[0] + 1;
  let autoLink = false;
  const words = [];
  const visible = [];
  for (const child of token.children) {
    if (child.type === 'link_open') autoLink = ['autolink', 'linkify'].includes(child.markup);
    if (child.type === 'link_close') autoLink = false;
    if (['softbreak', 'hardbreak'].includes(child.type)) line++;
    if (['text', 'code_inline'].includes(child.type) && !autoLink) visible.push(child.content);
    if (child.type !== 'text' || autoLink) continue;
    for (const word of child.content.replaceAll('’', "'").match(/[\p{L}]+(?:'[\p{L}]+)*/gu) ?? []) {
      words.push(word);
      if (!vocabulary.has(word)) vocabulary.set(word, line);
    }
  }
  if (tokens[index - 1]?.type === 'heading_open') {
    headings.push({ root: tokens[index - 1].level === 0, level: Number(tokens[index - 1].tag.slice(1)), title: token.content, visibleTitle: visible.join(''), line: token.map[0] + 1 });
  } else {
    paragraphs.push({ line: token.map[0] + 1, text: visible.join(' ') });
    if (listDepth && !quoteDepth && words.length) bullets.push({ line: token.map[0] + 1, text: words.join(' ') });
  }
}
const preferred = { color: 'colour', colors: 'colours', colored: 'coloured', behavior: 'behaviour',
  center: 'centre', centers: 'centres', centered: 'centred', gray: 'grey', catalog: 'catalogue' };
for (const [word, line] of vocabulary) {
  const base = word.replace(/'s$/i, '');
  if (terms.has(base) || terms.has(base.toLowerCase()) || spell.correct(word)) continue;
  const suggestion = preferred[word.toLowerCase()] ?? spell.suggest(word)[0];
  issues.push({ line, message: `Unknown UK English spelling '${word}'.${suggestion ? " Suggested spelling: " + suggestion + "." : ""} Put literal commands or identifiers in backticks.` });
}
process.stdout.write(JSON.stringify({ issues, headings, bullets, paragraphs }));
