export interface ServerModelOption {
  modelConfigId?: string;
  model?: string;
  customOption?: boolean;
}

export const filterRemoteModelOptions = <T extends ServerModelOption>(options: T[]) =>
  options.filter((item) => !item.customOption);

export const serverPresetRequestPayload = (option: ServerModelOption) => ({
  modelConfigId: option.modelConfigId,
  provider: undefined,
  model: undefined,
  apiKey: undefined,
  baseUrl: undefined,
  projectId: undefined,
  location: undefined,
  temperature: undefined,
  maxTokens: undefined,
});
