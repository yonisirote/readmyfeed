import { parseXFollowingTimelineResponse } from '../src/services/x/timeline/xTimelineParser';

describe('parseXFollowingTimelineResponse', () => {
  it('parses timeline tweets and next cursor', () => {
    const payload = {
      data: {
        home: {
          home_timeline_urt: {
            instructions: [
              {
                entries: [
                  {
                    content: {
                      __typename: 'TimelineTimelineItem',
                      itemContent: {
                        __typename: 'TimelineTweet',
                        tweet_results: {
                          result: {
                            __typename: 'Tweet',
                            rest_id: '111',
                            legacy: {
                              full_text: 'hello from x',
                              created_at: 'Thu Feb 20 12:34:56 +0000 2025',
                              lang: 'en',
                              quote_count: 1,
                              reply_count: 2,
                              retweet_count: 3,
                              favorite_count: 4,
                            },
                            core: {
                              user_results: {
                                result: {
                                  legacy: {
                                    name: 'Alice',
                                    screen_name: 'alice',
                                  },
                                },
                              },
                            },
                            views: {
                              count: '99',
                            },
                          },
                        },
                      },
                    },
                  },
                  {
                    content: {
                      __typename: 'TimelineTimelineCursor',
                      cursorType: 'Bottom',
                      value: 'cursor-abc',
                    },
                  },
                ],
              },
            ],
          },
        },
      },
      __typename: 'TimelineTweet',
      tweet_results: {
        result: {
          __typename: 'Tweet',
          rest_id: '111',
          legacy: {
            full_text: 'hello from x',
            created_at: 'Thu Feb 20 12:34:56 +0000 2025',
            lang: 'en',
            quote_count: 1,
            reply_count: 2,
            retweet_count: 3,
            favorite_count: 4,
          },
          core: {
            user_results: {
              result: {
                legacy: {
                  name: 'Alice',
                  screen_name: 'alice',
                },
              },
            },
          },
          views: {
            count: '99',
          },
        },
      },
      cursorType: 'Bottom',
      value: 'cursor-abc',
    };

    const parsed = parseXFollowingTimelineResponse(payload);

    expect(parsed.items).toHaveLength(1);
    expect(parsed.items[0].id).toBe('111');
    expect(parsed.items[0].authorHandle).toBe('alice');
    expect(parsed.items[0].viewCount).toBe(99);
    expect(parsed.nextCursor).toBe('cursor-abc');
  });
});
