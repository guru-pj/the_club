export const isHttpUrl = (value: string) => /^https?:\/\/\S+$/i.test(value.trim())
