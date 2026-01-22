const path = require('path');

const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

config.resolver.extraNodeModules = {
  ...(config.resolver.extraNodeModules ?? {}),
  axios: path.join(__dirname, 'node_modules/axios/dist/esm/axios.js'),
};

module.exports = config;
