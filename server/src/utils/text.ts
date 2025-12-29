const htmlEntityMap: Record<string, string> = {
  amp: '&',
  apos: "'",
  gt: '>',
  lt: '<',
  quot: '"',
};

function decodeNumericEntity(entityBody: string): string | undefined {
  const isHex = entityBody.startsWith('#x') || entityBody.startsWith('#X');
  const isDec = entityBody.startsWith('#');

  if (!isHex && !isDec) return undefined;

  const numberString = isHex ? entityBody.slice(2) : entityBody.slice(1);
  const codePoint = Number.parseInt(numberString, isHex ? 16 : 10);
  if (!Number.isFinite(codePoint)) return undefined;

  try {
    return String.fromCodePoint(codePoint);
  } catch {
    return undefined;
  }
}

export function decodeHtmlEntities(input: string): string {
  // Decode a small, relevant subset of entities.
  // Enough for tweet text (&gt;, &amp;, etc.) without adding deps.
  return input.replace(/&(#x?[0-9A-Fa-f]+|[A-Za-z]+);/g, (_match, body: string) => {
    const numeric = decodeNumericEntity(body);
    if (numeric !== undefined) return numeric;

    const named = htmlEntityMap[body];
    return named ?? `&${body};`;
  });
}
