import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <Stack>
        <Stack.Screen name="index" options={{ title: 'ReadMyFeed' }} />
        <Stack.Screen name="(auth)/x-login" options={{ title: 'X Login' }} />
        <Stack.Screen name="(auth)/x-feed" options={{ title: 'X Feed' }} />
      </Stack>
    </SafeAreaProvider>
  );
}
