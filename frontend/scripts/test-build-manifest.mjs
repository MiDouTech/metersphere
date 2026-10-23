import writeBuildManifest from './write-build-manifest.mjs';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

test('manifest binds the deployed index and its assets to the build revision', async () => {
  const directory = await mkdtemp(path.join(tmpdir(), 'msp-build-manifest-'));
  try {
    await writeFile(path.join(directory, 'index.html'), '<script type="module" src="/assets/index-a.js"></script>');
    const first = await writeBuildManifest(directory, 'revision-a');
    assert.equal(
      JSON.parse(await readFile(path.join(directory, 'build-manifest.json'), 'utf8')).revision,
      'revision-a'
    );
    assert.deepEqual(first.assets, ['/assets/index-a.js']);
    await writeFile(path.join(directory, 'index.html'), '<script type="module" src="/assets/index-b.js"></script>');
    const second = await writeBuildManifest(directory, 'revision-b');
    assert.notEqual(first.indexSha256, second.indexSha256);
    assert.deepEqual(second.assets, ['/assets/index-b.js']);
  } finally {
    assert.ok(path.resolve(directory).startsWith(path.join(path.resolve(tmpdir()), 'msp-build-manifest-')));
    await rm(directory, { recursive: true, force: true });
  }
});
