/// <reference types="node" />

import { webcrypto } from 'node:crypto';

import { DOMParser } from 'linkedom';

type GlobalWithPolyfills = typeof globalThis & {
  crypto?: typeof webcrypto;
  atob?: (input: string) => string;
  btoa?: (input: string) => string;
  DOMParser?: typeof DOMParser;
  window?: typeof globalThis;
};

const globalRef = globalThis as GlobalWithPolyfills;

if (!globalRef.crypto) {
  globalRef.crypto = webcrypto as Crypto;
}

if (!globalRef.atob) {
  globalRef.atob = (input: string) => Buffer.from(input, 'base64').toString('binary');
}

if (!globalRef.btoa) {
  globalRef.btoa = (input: string) => Buffer.from(input, 'binary').toString('base64');
}

if (!globalRef.DOMParser) {
  globalRef.DOMParser = DOMParser;
}

if (!globalRef.window) {
  globalRef.window = globalRef;
}

if (typeof ArrayBuffer !== 'undefined' && !ArrayBuffer.prototype.transfer) {
  Object.defineProperty(ArrayBuffer.prototype, 'transfer', {
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
