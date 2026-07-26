import assert from 'node:assert/strict';
import { filterRemoteModelOptions, serverPresetRequestPayload } from './serverModelOptionPolicy';

const remoteOptions = filterRemoteModelOptions([
  {
    modelConfigId: 'config:server-user-config',
    model: 'gpt-private',
    customOption: true,
  },
  {
    modelConfigId: 'preset:OPENAI:gpt-5.4',
    model: 'gpt-5.4',
    customOption: false,
  },
]);

assert.equal(remoteOptions.length, 1);
assert.equal(remoteOptions[0].modelConfigId, 'preset:OPENAI:gpt-5.4');
assert.deepEqual(serverPresetRequestPayload(remoteOptions[0]), {
  modelConfigId: 'preset:OPENAI:gpt-5.4',
  provider: undefined,
  model: undefined,
  apiKey: undefined,
  baseUrl: undefined,
  projectId: undefined,
  location: undefined,
  temperature: undefined,
  maxTokens: undefined,
});
