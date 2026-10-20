let conversations = [];

window.onMessaging = payload => {
  conversations = payload || [];
  console.log('[messaging] ' + conversations.length + ' bubbled: ' + conversations.map(conversation =>
    conversation.title + ' (' + conversation.messages.length + ' kept, ' + conversation.unread + ' unread' +
    (conversation.isLive ? '' : ', notification gone') + ')'
  ).join(', '));
};
