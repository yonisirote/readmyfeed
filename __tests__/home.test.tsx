import { render } from '@testing-library/react-native';

import HomeScreen from '../app/index';

describe('HomeScreen', () => {
  it('renders the app title', () => {
    const { getByText } = render(<HomeScreen />);

    expect(getByText('ReadMyFeed')).toBeTruthy();
  });
});
