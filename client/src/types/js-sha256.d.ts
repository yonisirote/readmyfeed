declare module 'js-sha256' {
  type Sha256Digest = {
    arrayBuffer: (message: ArrayBuffer | ArrayBufferView | string) => ArrayBuffer;
  };

  export const sha256: Sha256Digest;
  export default sha256;
}
