import { parseTimeline, toTweetSample } from './xTimelineParsing';

describe('xTimelineParsing', () => {
  it('parses entries and cursor from timeline payload', () => {
    const payload = {
      data: {
        home: {
          home_timeline_urt: {
            instructions: [
              {
                type: 'TimelineAddEntries',
                entries: [
                  {
                    entryId: 'tweet-1',
                    content: {
                      itemContent: {
                        tweet_results: {
                          result: {
                            __typename: 'Tweet',
                            rest_id: '111',
                            core: {
                              user_results: {
                                result: {
                                  core: {
                                    screen_name: 'alice',
                                  },
                                },
                              },
                            },
                            legacy: {
                              full_text: 'Hello world',
                            },
                          },
                        },
                      },
                    },
                  },
                  {
                    entryId: 'cursor-bottom-1',
                    content: {
                      value: 'cursor123',
                    },
                  },
                ],
              },
            ],
          },
        },
      },
    };

    const parsed = parseTimeline(payload);
    expect(parsed.entries).toHaveLength(2);
    expect(parsed.tweetEntries).toHaveLength(1);
    expect(parsed.nextCursor).toBe('cursor123');

    const sample = toTweetSample(parsed.tweetEntries[0].content.itemContent.tweet_results.result);
    expect(sample.user).toBe('alice');
    expect(sample.text).toBe('Hello world');
  });

  it('unwraps visibility results and handles retweets', () => {
    const payload = {
      data: {
        home: {
          home_timeline_urt: {
            instructions: [
              {
                type: 'TimelineAddEntries',
                entries: [
                  {
                    entryId: 'tweet-2',
                    content: {
                      itemContent: {
                        tweet_results: {
                          result: {
                            __typename: 'TweetWithVisibilityResults',
                            tweet: {
                              rest_id: '222',
                              core: {
                                user_results: {
                                  result: {
                                    core: {
                                      screen_name: 'bob',
                                    },
                                  },
                                },
                              },
                              legacy: {
                                retweeted_status_result: {
                                  result: {
                                    rest_id: '333',
                                    core: {
                                      user_results: {
                                        result: {
                                          core: {
                                            screen_name: 'carol',
                                          },
                                        },
                                      },
                                    },
                                    legacy: {
                                      full_text: 'Retweeted message',
                                    },
                                  },
                                },
                              },
                            },
                          },
                        },
                      },
                    },
                  },
                ],
              },
            ],
          },
        },
      },
    };

    const parsed = parseTimeline(payload);
    const sample = toTweetSample(parsed.tweetEntries[0].content.itemContent.tweet_results.result);
    expect(sample.id).toBe('222');
    expect(sample.user).toBe('bob');
    expect(sample.text).toBe('Retweeted message');
  });
});
