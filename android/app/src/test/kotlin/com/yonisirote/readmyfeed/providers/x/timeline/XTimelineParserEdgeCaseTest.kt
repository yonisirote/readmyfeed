package com.yonisirote.readmyfeed.providers.x.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XTimelineParserEdgeCaseTest {

  // ── 1. Invalid JSON throws XTimelineException with RESPONSE_INVALID ──

  @Test
  fun invalidJsonThrowsXTimelineExceptionWithResponseInvalidCode() {
    try {
      parseXFollowingTimelineResponse("not json at all {{{")
      throw AssertionError("Expected XTimelineException")
    } catch (e: XTimelineException) {
      assertEquals(XTimelineErrorCodes.RESPONSE_INVALID, e.code)
    }
  }

  // ── 2. Empty JSON object returns empty items and null cursor ──

  @Test
  fun emptyJsonObjectReturnsEmptyItemsAndNullCursor() {
    val result = parseXFollowingTimelineResponse("{}")
    assertTrue(result.items.isEmpty())
    assertNull(result.nextCursor)
  }

  // ── 3. No TimelineTweet nodes returns empty items ──

  @Test
  fun noTimelineTweetNodesReturnsEmptyItems() {
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
                        "__typename": "TimelineTimelineCursor",
                        "cursorType": "Top",
                        "value": "top-cursor"
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

    val result = parseXFollowingTimelineResponse(payload)
    assertTrue(result.items.isEmpty())
  }

  // ── 4. Duplicate tweet IDs — first occurrence wins ──

  @Test
  fun duplicateTweetIdsFirstOccurrenceWins() {
    val payload = """
      {
        "items": [
          {
            "__typename": "TimelineTweet",
            "tweet_results": {
              "result": {
                "__typename": "Tweet",
                "rest_id": "dup1",
                "legacy": {
                  "full_text": "first",
                  "created_at": "",
                  "lang": "en",
                  "quote_count": 0,
                  "reply_count": 0,
                  "retweet_count": 0,
                  "favorite_count": 0
                },
                "core": {
                  "user_results": {
                    "result": {
                      "legacy": { "name": "A", "screen_name": "a" }
                    }
                  }
                }
              }
            }
          },
          {
            "__typename": "TimelineTweet",
            "tweet_results": {
              "result": {
                "__typename": "Tweet",
                "rest_id": "dup1",
                "legacy": {
                  "full_text": "second",
                  "created_at": "",
                  "lang": "en",
                  "quote_count": 0,
                  "reply_count": 0,
                  "retweet_count": 0,
                  "favorite_count": 0
                },
                "core": {
                  "user_results": {
                    "result": {
                      "legacy": { "name": "B", "screen_name": "b" }
                    }
                  }
                }
              }
            }
          }
        ]
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items.size)
    assertEquals("first", result.items[0].text)
  }

  // ── 5. Tweet with blank rest_id is skipped ──

  @Test
  fun tweetWithBlankRestIdIsSkipped() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "",
            "legacy": {
              "full_text": "ghost tweet",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "X", "screen_name": "x" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertTrue(result.items.isEmpty())
  }

  // ── 6. Tweet with no legacy object is skipped ──

  @Test
  fun tweetWithNoLegacyObjectIsSkipped() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "no-legacy",
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "X", "screen_name": "x" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertTrue(result.items.isEmpty())
  }

  // ── 7. TweetWithVisibilityResults is properly unwrapped ──

  @Test
  fun tweetWithVisibilityResultsIsUnwrapped() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "TweetWithVisibilityResults",
            "tweet": {
              "__typename": "Tweet",
              "rest_id": "vis1",
              "legacy": {
                "full_text": "visibility wrapped",
                "created_at": "",
                "lang": "en",
                "quote_count": 0,
                "reply_count": 0,
                "retweet_count": 0,
                "favorite_count": 0
              },
              "core": {
                "user_results": {
                  "result": {
                    "legacy": { "name": "Viz", "screen_name": "viz" }
                  }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items.size)
    assertEquals("vis1", result.items[0].id)
    assertEquals("visibility wrapped", result.items[0].text)
    assertEquals("viz", result.items[0].authorHandle)
  }

  // ── 8. Note tweet (long form) text overrides legacy full_text ──

  @Test
  fun noteTweetTextOverridesLegacyFullText() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "note1",
            "legacy": {
              "full_text": "short legacy text",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "note_tweet": {
              "note_tweet_results": {
                "result": {
                  "text": "this is a much longer note tweet with extended content"
                }
              }
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "N", "screen_name": "noter" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items.size)
    assertEquals(
      "this is a much longer note tweet with extended content",
      result.items[0].text,
    )
  }

  // ── 9. Retweet detection ──

  @Test
  fun retweetDetectedViaRetweetedStatusResult() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "rt1",
            "legacy": {
              "full_text": "RT @someone: original",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0,
              "retweeted_status_result": {
                "result": {
                  "rest_id": "orig1"
                }
              }
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "R", "screen_name": "retweeter" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items.size)
    assertTrue(result.items[0].isRetweet)
  }

  // ── 10. Media extraction — photo and video ──

  @Test
  fun mediaExtractionPhotoAndVideo() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "media1",
            "legacy": {
              "full_text": "media tweet",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0,
              "extended_entities": {
                "media": [
                  {
                    "type": "photo",
                    "media_url_https": "https://pbs.twimg.com/photo1.jpg",
                    "expanded_url": "https://x.com/u/status/1/photo/1"
                  },
                  {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/thumb.jpg",
                    "expanded_url": "https://x.com/u/status/1/video/1",
                    "video_info": {
                      "variants": [
                        {
                          "content_type": "video/mp4",
                          "url": "https://video.twimg.com/vid.mp4",
                          "bitrate": 2176000
                        }
                      ]
                    }
                  }
                ]
              }
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "M", "screen_name": "mediaposter" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(2, result.items[0].media.size)

    val photo = result.items[0].media[0]
    assertEquals(XTimelineMediaType.PHOTO, photo.type)
    assertEquals("https://pbs.twimg.com/photo1.jpg", photo.url)
    assertNull(photo.thumbnailUrl)

    val video = result.items[0].media[1]
    assertEquals(XTimelineMediaType.VIDEO, video.type)
    assertEquals("https://video.twimg.com/vid.mp4", video.url)
    assertEquals("https://pbs.twimg.com/thumb.jpg", video.thumbnailUrl)
  }

  // ── 11. Video media picks highest bitrate mp4 ──

  @Test
  fun videoMediaPicksHighestBitrateMp4() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "vid1",
            "legacy": {
              "full_text": "video tweet",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0,
              "extended_entities": {
                "media": [
                  {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/thumb.jpg",
                    "expanded_url": "https://x.com/u/status/1/video/1",
                    "video_info": {
                      "variants": [
                        {
                          "content_type": "application/x-mpegURL",
                          "url": "https://video.twimg.com/playlist.m3u8"
                        },
                        {
                          "content_type": "video/mp4",
                          "url": "https://video.twimg.com/low.mp4",
                          "bitrate": 256000
                        },
                        {
                          "content_type": "video/mp4",
                          "url": "https://video.twimg.com/high.mp4",
                          "bitrate": 2176000
                        },
                        {
                          "content_type": "video/mp4",
                          "url": "https://video.twimg.com/mid.mp4",
                          "bitrate": 832000
                        }
                      ]
                    }
                  }
                ]
              }
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "V", "screen_name": "vidposter" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items[0].media.size)
    assertEquals("https://video.twimg.com/high.mp4", result.items[0].media[0].url)
  }

  // ── 12. Animated GIF media type detection ──

  @Test
  fun animatedGifMediaTypeDetection() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "gif1",
            "legacy": {
              "full_text": "gif tweet",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0,
              "extended_entities": {
                "media": [
                  {
                    "type": "animated_gif",
                    "media_url_https": "https://pbs.twimg.com/gif-thumb.jpg",
                    "expanded_url": "https://x.com/u/status/1/photo/1",
                    "video_info": {
                      "variants": [
                        {
                          "content_type": "video/mp4",
                          "url": "https://video.twimg.com/gif.mp4",
                          "bitrate": 0
                        }
                      ]
                    }
                  }
                ]
              }
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "G", "screen_name": "gifposter" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items[0].media.size)
    assertEquals(XTimelineMediaType.ANIMATED_GIF, result.items[0].media[0].type)
    assertEquals("https://video.twimg.com/gif.mp4", result.items[0].media[0].url)
    assertEquals("https://pbs.twimg.com/gif-thumb.jpg", result.items[0].media[0].thumbnailUrl)
  }

  // ── 13. Unknown media type ──

  @Test
  fun unknownMediaType() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "unk1",
            "legacy": {
              "full_text": "unknown media",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0,
              "extended_entities": {
                "media": [
                  {
                    "type": "hologram",
                    "media_url_https": "",
                    "expanded_url": "https://x.com/u/status/1/hologram/1",
                    "video_info": {
                      "variants": [
                        {
                          "content_type": "video/mp4",
                          "url": "https://video.twimg.com/holo.mp4",
                          "bitrate": 100
                        }
                      ]
                    }
                  }
                ]
              }
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "U", "screen_name": "unkposter" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items[0].media.size)
    assertEquals(XTimelineMediaType.UNKNOWN, result.items[0].media[0].type)
  }

  // ── 14. Empty media when extended_entities is missing ──

  @Test
  fun emptyMediaWhenExtendedEntitiesIsMissing() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "nomedia1",
            "legacy": {
              "full_text": "text only",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "T", "screen_name": "textonly" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items.size)
    assertTrue(result.items[0].media.isEmpty())
  }

  // ── 15. ISO date passthrough ──

  @Test
  fun isoDatePassthrough() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "iso1",
            "legacy": {
              "full_text": "iso date tweet",
              "created_at": "2025-02-20T12:34:56Z",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "I", "screen_name": "isoposter" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items.size)
    assertEquals("2025-02-20T12:34:56Z", result.items[0].createdAt)
  }

  // ── 16. Blank createdAt results in empty string ──

  @Test
  fun blankCreatedAtResultsInEmptyString() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "blank1",
            "legacy": {
              "full_text": "no date tweet",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "B", "screen_name": "blankdate" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals("", result.items[0].createdAt)
  }

  // ── 17. viewCount is null when views object is missing ──

  @Test
  fun viewCountIsNullWhenViewsObjectIsMissing() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "noview1",
            "legacy": {
              "full_text": "no views",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "N", "screen_name": "noviews" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertNull(result.items[0].viewCount)
  }

  // ── 18. URL is empty when authorHandle is blank ──

  @Test
  fun urlIsEmptyWhenAuthorHandleIsBlank() {
    val payload = """
      {
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "noauth1",
            "legacy": {
              "full_text": "no author handle",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "No Handle" },
                  "core": {}
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items.size)
    assertEquals("", result.items[0].url)
    assertEquals("", result.items[0].authorHandle)
  }

  // ── 19. Cursor found via fallback findByFilter ──

  @Test
  fun cursorFoundViaFallbackFindByFilter() {
    val payload = """
      {
        "somewhere": {
          "nested": {
            "cursorType": "Bottom",
            "value": "fallback-cursor-123"
          }
        },
        "__typename": "TimelineTweet",
        "tweet_results": {
          "result": {
            "__typename": "Tweet",
            "rest_id": "fb1",
            "legacy": {
              "full_text": "fallback cursor tweet",
              "created_at": "",
              "lang": "en",
              "quote_count": 0,
              "reply_count": 0,
              "retweet_count": 0,
              "favorite_count": 0
            },
            "core": {
              "user_results": {
                "result": {
                  "legacy": { "name": "F", "screen_name": "fallbacker" }
                }
              }
            }
          }
        }
      }
    """.trimIndent()

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals(1, result.items.size)
    assertEquals("fallback-cursor-123", result.nextCursor)
  }

  // ── 20. Multiple instructions — cursor from last instruction ──

  @Test
  fun cursorFromLastInstruction() {
    val payload = """
      {
        "data": {
          "home": {
            "home_timeline_urt": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    {
                      "content": {
                        "cursorType": "Bottom",
                        "value": "first-cursor"
                      }
                    }
                  ]
                },
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    {
                      "content": {
                        "cursorType": "Bottom",
                        "value": "last-cursor"
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

    val result = parseXFollowingTimelineResponse(payload)
    assertEquals("last-cursor", result.nextCursor)
  }
}
