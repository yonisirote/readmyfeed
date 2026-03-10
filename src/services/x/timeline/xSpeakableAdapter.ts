import type { SpeakableItem } from '../../../tts';
import type { XTimelineItem } from './xTimelineTypes';

export const toSpeakableItem = (item: XTimelineItem): SpeakableItem => {
  const prefix = item.isRetweet ? 'Reposted. ' : '';
  let body = item.text;

  if (item.isQuote && item.quotedText) {
    const quoteAttribution = item.quotedAuthorHandle
      ? `Quoting ${item.quotedAuthorHandle}. `
      : 'Quote. ';
    body = `${body} ${quoteAttribution}${item.quotedText}`;
  }

  return {
    text: `${prefix}${body}`.trim(),
    authorLabel: item.authorName || item.authorHandle,
    lang: item.lang,
  };
};
