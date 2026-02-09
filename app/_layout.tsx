import { Stack } from 'expo-router';

export default function RootLayout() {
  return (
    <Stack screenOptions={{ headerTitle: 'ReadMyFeed' }}>
      <Stack.Screen name="index" options={{ title: 'ReadMyFeed' }} />
      <Stack.Screen name="(auth)/x-login" options={{ title: 'Connect X' }} />
    </Stack>
  );
}
