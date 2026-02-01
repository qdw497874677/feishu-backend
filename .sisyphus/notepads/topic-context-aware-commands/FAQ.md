# FAQ - Topic-Aware Command Execution

**Last Updated:** 2026-01-31 18:30

---

## General Questions

### Q1: What is this feature?

**A:** This feature allows you to execute commands in Feishu topics WITHOUT typing the command prefix. For example, in a bash topic, you can type `pwd` instead of `/bash pwd`.

### Q2: How does it work?

**A:** When you send a message in a topic that was created by an app (like bash), the bot automatically adds the appropriate prefix before executing the command. So `pwd` becomes `/bash pwd` automatically.

### Q3: Do I still need the prefix in normal chat?

**A:** Yes. In normal (non-topic) chat, you still need to type the full command like `/bash pwd`. The prefix-free feature only works inside topics.

---

## Usage Questions

### Q4: How do I create a topic?

**A:** Simply send a command with the prefix in normal chat. For example:
1. Send: `/bash pwd` in normal chat
2. Bot will reply and create a topic
3. Now you can use prefix-free commands in that topic

### Q5: Can I still use the prefix in topics?

**A:** Yes! The feature is backward compatible. You can type either:
- `pwd` (without prefix)
- `/bash pwd` (with prefix)

Both will work correctly in topics.

### Q6: What if I type the wrong prefix?

**A:** The bot will treat it as a new command. For example, if you type `/time pwd` in a bash topic, it will be handled as a time command, not a bash command.

### Q7: Does this work for all apps?

**A:** Yes, it works for any app that creates topics. Currently supported:
- `bash` - Execute bash commands
- `time` - Get current time
- `help` - Show help information
- `history` - View command history

---

## Technical Questions

### Q8: What happens if I'm in a topic without an app mapping?

**A:** You'll see an error message: "话题已失效" (Topic has expired). This means the topic is no longer associated with an app. Send a new command with prefix to create a fresh topic.

### Q9: Are my commands logged?

**A:** Yes, all commands are logged for debugging purposes. The logs show:
- When a prefix is added: `"话题中的消息不包含前缀，添加前缀: 'pwd'"`
- When a prefix is stripped: `"话题中的消息包含命令前缀，去除前缀: '/bash pwd'"`

### Q10: What about command arguments?

**A:** Command arguments work perfectly. For example:
- `ls -la /tmp` → becomes `/bash ls -la /tmp`
- Arguments are preserved exactly as you type them

### Q11: Does whitespace matter?

**A:** Extra whitespace is automatically trimmed. So `  pwd  ` will be treated the same as `pwd`.

---

## Error Handling

### Q12: What if my command fails?

**A:** If a command fails, you'll see an error message from the app. For bash commands, you'll see the standard error output.

### Q13: What if I see "话题已失效"?

**A:** This means the topic is no longer mapped to an app. To fix:
1. Go to normal chat (not topic)
2. Send `/bash <your command>` to create a new topic
3. Continue using prefix-free commands in the new topic

### Q14: What if the bot doesn't respond?

**A:** Check the following:
1. Is the application running? `ps aux | grep Application`
2. Is WebSocket connected? Check logs for "connected to wss://"
3. Is your command valid? Try a simple command like `pwd`

---

## Testing Questions

### Q15: How do I test this feature?

**A:** Follow these steps:
1. Send `/bash pwd` in normal chat
2. Bot creates a topic
3. In that topic, send: `pwd` (no prefix!)
4. Bot should execute the command

### Q16: What should I see in the logs?

**A:** When testing, look for these log messages:
```
话题中的消息不包含前缀，添加前缀: 'pwd'
话题消息处理后的内容: '/bash pwd'
```

### Q17: How long does testing take?

**A:** About 10-15 minutes to run all 4 test cases:
- Test 1: Topic without prefix (2 min)
- Test 2: Topic with prefix (2 min)
- Test 3: Normal chat (2 min)
- Test 4: Whitelist commands (2 min)

---

## Security & Safety

### Q18: Is this feature safe?

**A:** Yes. The feature:
- ✅ Still uses command whitelist validation
- ✅ Only works in mapped topics
- ✅ Doesn't bypass any security checks
- ✅ Is fully backward compatible

### Q19: Can I execute any command without prefix?

**A:** No. You can only execute commands that are:
1. In the command whitelist
2. Supported by the app that owns the topic
3. Valid commands for that app

For example, `mkdir test_dir` works because `mkdir` is whitelisted.

### Q20: What if someone tries to execute malicious commands?

**A:** The whitelist prevents unauthorized commands. Commands not in the whitelist will be rejected with an error message.

---

## Troubleshooting

### Q21: The feature isn't working for me

**A:** Try these steps:
1. Make sure you're in a topic (not normal chat)
2. Make sure the topic was created by an app
3. Check application logs: `tail -f /tmp/feishu-run.log | grep '话题'`
4. Try with a simple command like `pwd`

### Q22: I see an error message

**A:** Check the error message:
- "话题已失效" → Create a new topic
- "command not found" → Check your spelling
- "whitelist error" → Command not allowed

### Q23: Bot responds but command doesn't execute

**A:** This could mean:
1. Command syntax is wrong
2. Command requires arguments
3. Command failed (bash error)
4. Check the bot's response for details

---

## Advanced Questions

### Q24: Can I disable this feature?

**A:** To disable, rollback the code changes. See `ROLLBACK-PLAN.md` for instructions.

### Q25: Does this work with nested topics?

**A:** Yes, as long as the topic has an app mapping. Each topic maintains its own mapping.

### Q26: What happens to existing topics?

**A:** Existing topics with app mappings will automatically support prefix-free commands. No migration needed.

### Q27: Can I use this in group chats?

**A:** Yes, as long as the topic was created by the bot in the group chat.

---

## Performance & Impact

### Q28: Does this slow down command execution?

**A:** No. The prefix manipulation is a simple string operation that takes microseconds. No noticeable performance impact.

### Q29: Does this use more memory?

**A:** Negligible. The feature adds minimal memory overhead for storing topic mappings.

### Q30: What's the impact on server load?

**A:** None. The feature doesn't add any additional server calls or database queries.

---

## Future & Updates

### Q31: Will more commands be added to the whitelist?

**A:** Yes, as needed. The whitelist can be extended to support more safe commands.

### Q32: Will this feature work with new apps?

**A:** Yes, any new app that creates topics will automatically support prefix-free commands.

### Q33: Can I suggest improvements?

**A:** Absolutely! Share your feedback with the development team.

---

## Quick Reference

### Topic Commands (No Prefix Needed)
```bash
pwd              # Show current directory
ls               # List files
ls -la           # List files with details
mkdir test_dir   # Create directory
cat file.txt     # Read file
```

### Normal Chat Commands (Prefix Required)
```bash
/bash pwd        # Execute bash command
/time now        # Get current time
/help            # Show help
/history         # View history
```

### Log Monitoring
```bash
# Watch topic processing
tail -f /tmp/feishu-run.log | grep '话题'

# Check for errors
tail -f /tmp/feishu-run.log | grep 'ERROR'

# See all message processing
tail -f /tmp/feishu-run.log | grep 'Processing'
```

---

## Need More Help?

**Documentation:**
- `README-NEXT-STEPS.md` - Quick start guide
- `QUICK-REFERENCE.md` - 2-page testing guide
- `ROLLBACK-PLAN.md` - If something goes wrong

**Commands:**
- `./status-check.sh` - Check application status
- `./run-tests.sh` - Run all test cases

**Logs:**
- Application log: `/tmp/feishu-run.log`

---

**Last Updated:** 2026-01-31 18:30
**Version:** 1.0.0
**Status:** Feature Complete
