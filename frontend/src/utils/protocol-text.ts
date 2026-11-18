export default function protocolTextSize(value: string) {
  const normalized = value.normalize('NFC');
  const scalars = Array.from(normalized);
  return {
    scalars: scalars.length,
    bytes: new TextEncoder().encode(normalized).byteLength,
    valid: scalars.every((character) => {
      const code = character.codePointAt(0)!;
      return code < 0xd800 || code > 0xdfff;
    }),
  };
}
