import { Stack } from 'expo-router';

export default function RootLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'ReadMyFeed' }} />
      <Stack.Screen name="(auth)/x-login" options={{ title: 'X Login' }} />
      <Stack.Screen name="(auth)/x-feed" options={{ title: 'X Feed' }} />
    </Stack>
  );
}
