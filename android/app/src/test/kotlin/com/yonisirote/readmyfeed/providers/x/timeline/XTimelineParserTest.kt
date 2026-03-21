package com.yonisirote.readmyfeed.providers.x.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XTimelineParserTest {
  @Test
  fun parsesTimelineTweetsAndNextCursor() {
    val payload = """
      {
        "data": {
          "home": {
            "home_timeline_urt": {
              "instructions": [
                {
                  "entries": [
                    {
                      "content": {
                        "__typename": "TimelineTimelineItem",
                        "itemContent": {
                          "__typename": "TimelineTweet",
                          "tweet_results": {
                            "result": {
                              "__typename": "Tweet",
                              "rest_id": "111",
                              "legacy": {
                                "full_text": "hello from x",
                                "created_at": "Thu Feb 20 12:34:56 +0000 2025",
                                "lang": "en",
                                "quote_count": 1,
                                "reply_count": 2,
                                "retweet_count": 3,
                                "favorite_count": 4
                              },
                              "core": {
                                "user_results": {
                                  "result": {
                                    "legacy": {
                                      "name": "Alice",
                                      "screen_name": "alice"
                                    }
                                  }
                                }
                              },
                              "views": {
                                "count": "99"
                              }
                            }
                          }
                        }
                      }
                    },
                    {
                      "content": {
                        "__typename": "TimelineTimelineCursor",
                        "cursorType": "Bottom",
                        "value": "cursor-abc"
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
      }
    """.trimIndent()

    val parsed = parseXFollowingTimelineResponse(payload)

    assertEquals(1, parsed.items.size)
    assertEquals("111", parsed.items[0].id)
    assertEquals("alice", parsed.items[0].authorHandle)
    assertEquals(99, parsed.items[0].viewCount)
    assertEquals("cursor-abc", parsed.nextCursor)
  }

  @Test
  fun extractsQuotedTweetTextAndAuthor() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "222",
            "legacy": {
              "full_text": "my commentary",
              "created_at": "Thu Feb 20 12:34:56 +0000 2025",
              "lang": "en",
              "is_quote_status": true,
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": {
                    "name": "Bob",
                    "screen_name": "bob"
                  }
                }
              }
            },
            "quoted_status_result": {
              "result": {
                "__typename": "Tweet",
                "rest_id": "333",
                "legacy": {
                  "full_text": "original thought",
                  "lang": "he",
                  "extended_entities": {
                    "media": [
                      {
                        "type": "photo",
                        "media_url_https": "https://example.com/quote-photo.jpg",
                        "expanded_url": "https://x.com/alice/status/333/photo/1"
                      }
                    ]
                  }
                },
                "core": {
                  "user_results": {
                    "result": {
                      "legacy": {
                        "name": "Alice",
                        "screen_name": "alice"
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val parsed = parseXFollowingTimelineResponse(payload)

    assertEquals(1, parsed.items.size)
    assertTrue(parsed.items[0].isQuote)
    assertEquals("original thought", parsed.items[0].quotedText)
    assertEquals("he", parsed.items[0].quotedLang)
    assertEquals("alice", parsed.items[0].quotedAuthorHandle)
    assertEquals(1, parsed.items[0].quotedMedia.size)
    assertEquals(XTimelineMediaType.PHOTO, parsed.items[0].quotedMedia[0].type)
    assertEquals("https://example.com/quote-photo.jpg", parsed.items[0].quotedMedia[0].url)
  }

  @Test
  fun readsAuthorFromUserCoreWhenLegacyLacksName() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "444",
            "legacy": {
              "full_text": "hello again",
              "created_at": "Thu Feb 20 12:34:56 +0000 2025",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": {},
                  "core": {
                    "name": "Charlie",
                    "screen_name": "charlie"
                  }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val parsed = parseXFollowingTimelineResponse(payload)

    assertEquals(1, parsed.items.size)
    assertEquals("charlie", parsed.items[0].authorHandle)
    assertEquals("Charlie", parsed.items[0].authorName)
  }

  @Test
  fun findsLateBottomCursorFromTimelineInstructions() {
    // Put the bottom cursor at the very end so reverse scanning keeps winning on large payloads.
    val tweetEntries = (0 until 200).joinToString(separator = ",") { index ->
      """
        {
          "entryId": "tweet-$index",
          "content": {
            "__typename": "TimelineTimelineItem",
            "itemContent": {
              "__typename": "TimelineTweet",
              "tweet_results": {
                "result": {
                  "__typename": "Tweet",
                  "rest_id": "${index + 1}",
                  "legacy": {
                    "full_text": "tweet ${index + 1}",
                    "created_at": "Thu Feb 20 12:34:56 +0000 2025",
                    "lang": "en",
                    "quote_count": 0,
                    "reply_count": 0,
                    "retweet_count": 0,
                    "favorite_count": 0
                  },
                  "core": {
                    "user_results": {
                      "result": {
                        "legacy": {
                          "name": "Alice",
                          "screen_name": "alice"
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      """.trimIndent()
    }

    val payload = """
      {
        "data": {
          "home": {
            "home_timeline_urt": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    $tweetEntries,
                    {
                      "entryId": "cursor-bottom",
                      "content": {
                        "__typename": "TimelineTimelineCursor",
                        "cursorType": "Bottom",
                        "value": "cursor-late"
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
      }
    """.trimIndent()

    val parsed = parseXFollowingTimelineResponse(payload)

    assertEquals(200, parsed.items.size)
    assertEquals("cursor-late", parsed.nextCursor)
  }
}
