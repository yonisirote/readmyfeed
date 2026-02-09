import { Link } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

export default function HomeScreen() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>ReadMyFeed</Text>
      <Text style={styles.subtitle}>Connect feeds and listen hands-free.</Text>
      <Link href="/(auth)/x-login" style={styles.link}>
        Connect X
      </Link>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 16,
  },
  link: {
    fontSize: 16,
    color: '#1d4ed8',
    fontWeight: '600',
  },
});
