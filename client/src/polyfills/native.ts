import "fast-text-encoding";
import "react-native-get-random-values";
import "react-native-url-polyfill/auto";

import { decode as atob, encode as btoa } from "base-64";
import webCrypto, { polyfillWebCrypto } from "expo-standard-web-crypto";
import { sha256 } from "js-sha256";
import { DOMParser } from "linkedom";

type CryptoSubtle = {
  digest: (
    algorithm: string,
    data: ArrayBuffer | ArrayBufferView,
  ) => Promise<ArrayBuffer>;
};

type CryptoLike = typeof webCrypto & { subtle?: CryptoSubtle };

type GlobalWithPolyfills = Omit<typeof globalThis, "crypto"> & {
  crypto?: CryptoLike;
  atob?: (input: string) => string;
  btoa?: (input: string) => string;
  DOMParser?: typeof globalThis.DOMParser;
};

const globalRef = globalThis as GlobalWithPolyfills;

try {
  polyfillWebCrypto();
} catch {
  // Ignore if already applied or unsupported in this runtime.
}

if (!globalRef.crypto) {
  globalRef.crypto = webCrypto as unknown as CryptoLike;
}

if (!globalRef.crypto?.subtle) {
  const cryptoRef = globalRef.crypto as CryptoLike;
  cryptoRef.subtle = {
    async digest(algorithm: string, data: ArrayBuffer | ArrayBufferView) {
      const normalized = algorithm.toUpperCase();
      if (normalized !== "SHA-256") {
        throw new Error(`Unsupported digest algorithm: ${algorithm}`);
      }

      const view =
        data instanceof ArrayBuffer
          ? new Uint8Array(data)
          : new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
      return sha256.arrayBuffer(view);
    },
  };
}

if (!globalRef.atob) {
  globalRef.atob = atob;
}

if (!globalRef.btoa) {
  globalRef.btoa = btoa;
}

if (!globalRef.DOMParser) {
  globalRef.DOMParser = DOMParser as unknown as typeof globalThis.DOMParser;
}

if (typeof ArrayBuffer !== "undefined" && !ArrayBuffer.prototype.transfer) {
  Object.defineProperty(ArrayBuffer.prototype, "transfer", {
    value(newLength: number) {
      const source = new Uint8Array(this);
      const target = new Uint8Array(new ArrayBuffer(newLength));
      target.set(source.subarray(0, Math.min(source.length, target.length)));
      return target.buffer;
    },
    writable: true,
    configurable: true,
  });
}

const globalWithWindow = globalThis as Omit<typeof globalThis, "window"> & {
  window?: typeof globalThis;
};

if (!globalWithWindow.window) {
  globalWithWindow.window = globalThis;
}
