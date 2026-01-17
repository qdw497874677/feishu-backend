#!/bin/bash
set -e
cd /root/workspace/feishu-backend/feishu-bot

# Initialize git
git init

# Add all files
git add .

# Create initial commit
git commit -m "$(cat <<'EOF'
feat: Initial COLA architecture implementation for Feishu bot backend

🏗 Architecture: Clean 4-layer COLA v5.0.0
- Adapter → App → Domain → Infrastructure
- Gateway pattern for Feishu SDK isolation
- Extension point mechanism for pluggable reply strategies

✅ Implemented Components:
- Adapter Layer: FeishuWebhookController
- Client Layer: MessageServiceI, ReceiveMessageCmd, ReceiveMessageQry
- App Layer: ReceiveMessageCmdExe
- Domain Layer: Message entity, FeishuGateway, ReplyExtensionPt, EchoReplyExtension
- Infrastructure Layer: FeishuGatewayImpl, FeishuProperties
- Start Module: Application with Spring Boot 3.x, JDK 17

📦 Tech Stack:
- COLA Framework 5.0.0
- Feishu SDK 2.4.22
- Spring Boot 3.2.1
- Lombok 1.18.30
- SLF4J 2.0.9

📝 Documentation:
- DEVELOPMENT_EXPERIENCE.md: Architecture lessons and best practices
- README.md: Quick start guide

Created by Sisyphus with subagent-driven development
EOF
)"

# Add remote repository (SSH style)
git remote add origin git@github.com:qdw497874677/feishu-backend.git

# Push to remote
git push -u origin master
