# AWS デプロイメントガイド

## 1. AWS デプロイメント概要

### 1.1 AWS アーキテクチャ設計
```
┌─────────────────────────────────────────────────────────────────┐
│                        AWS Cloud                                │
│                                                                 │
│  ┌──────────────────┐    ┌───────────────────┐                │
│  │   Amazon ECS     │    │   Amazon RDS      │                │
│  │   (Fargate)      │    │   (Oracle)        │                │
│  │                  │    │                   │                │
│  │ ┌──────────────┐ │    │ ┌───────────────┐ │                │
│  │ │csv-batch-    │ │◄───┤ │ Oracle DB     │ │                │
│  │ │processor     │ │    │ │ (XEPDB1)      │ │                │
│  │ └──────────────┘ │    │ └───────────────┘ │                │
│  │ ┌──────────────┐ │    │                   │                │
│  │ │soap-stub     │ │    │                   │                │
│  │ └──────────────┘ │    │                   │                │
│  └──────────────────┘    └───────────────────┘                │
│           │                                                    │
│           ▼                                                    │
│  ┌──────────────────┐    ┌───────────────────┐                │
│  │   Amazon S3      │    │   CloudWatch      │                │
│  │   (CSV Output)   │    │   (Monitoring)    │                │
│  └──────────────────┘    └───────────────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 使用AWSサービス
- **Amazon ECS (Fargate)**: コンテナオーケストレーション
- **Amazon RDS (Oracle)**: マネージドデータベース
- **Amazon S3**: CSV出力ファイルストレージ
- **Amazon ECR**: コンテナイメージレジストリ
- **Amazon VPC**: ネットワーク分離
- **AWS CloudWatch**: ログ監視・メトリクス
- **AWS IAM**: アクセス制御
- **Amazon EventBridge**: スケジュール実行

## 2. 事前準備

### 2.1 AWS CLI設定
```bash
# AWS CLIインストール確認
aws --version

# AWS認証情報設定
aws configure
# AWS Access Key ID: YOUR_ACCESS_KEY
# AWS Secret Access Key: YOUR_SECRET_KEY
# Default region name: ap-northeast-1
# Default output format: json

# 認証確認
aws sts get-caller-identity
```

### 2.2 必要な権限
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "ecs:*",
                "ecr:*",
                "rds:*",
                "s3:*",
                "iam:*",
                "logs:*",
                "events:*",
                "ec2:*"
            ],
            "Resource": "*"
        }
    ]
}
```

### 2.3 Docker設定
```bash
# Docker Desktopが起動していることを確認
docker info

# マルチプラットフォームビルド設定
docker buildx create --name mybuilder --use
docker buildx inspect --bootstrap
```

## 3. インフラストラクチャ構築

### 3.1 VPC とネットワーク構築
```bash
# VPC作成
VPC_ID=$(aws ec2 create-vpc \
    --cidr-block 10.0.0.0/16 \
    --tag-specifications 'ResourceType=vpc,Tags=[{Key=Name,Value=csv-batch-vpc}]' \
    --query 'Vpc.VpcId' --output text)

# インターネットゲートウェイ作成
IGW_ID=$(aws ec2 create-internet-gateway \
    --tag-specifications 'ResourceType=internet-gateway,Tags=[{Key=Name,Value=csv-batch-igw}]' \
    --query 'InternetGateway.InternetGatewayId' --output text)

# インターネットゲートウェイをVPCにアタッチ
aws ec2 attach-internet-gateway \
    --vpc-id $VPC_ID \
    --internet-gateway-id $IGW_ID

# パブリックサブネット作成（AZ-a）
PUBLIC_SUBNET_A=$(aws ec2 create-subnet \
    --vpc-id $VPC_ID \
    --cidr-block 10.0.1.0/24 \
    --availability-zone ap-northeast-1a \
    --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=csv-batch-public-a}]' \
    --query 'Subnet.SubnetId' --output text)

# パブリックサブネット作成（AZ-b）
PUBLIC_SUBNET_B=$(aws ec2 create-subnet \
    --vpc-id $VPC_ID \
    --cidr-block 10.0.2.0/24 \
    --availability-zone ap-northeast-1c \
    --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=csv-batch-public-b}]' \
    --query 'Subnet.SubnetId' --output text)

# プライベートサブネット作成（AZ-a）
PRIVATE_SUBNET_A=$(aws ec2 create-subnet \
    --vpc-id $VPC_ID \
    --cidr-block 10.0.3.0/24 \
    --availability-zone ap-northeast-1a \
    --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=csv-batch-private-a}]' \
    --query 'Subnet.SubnetId' --output text)

# プライベートサブネット作成（AZ-b）
PRIVATE_SUBNET_B=$(aws ec2 create-subnet \
    --vpc-id $VPC_ID \
    --cidr-block 10.0.4.0/24 \
    --availability-zone ap-northeast-1c \
    --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=csv-batch-private-b}]' \
    --query 'Subnet.SubnetId' --output text)

# ルートテーブル設定
ROUTE_TABLE_ID=$(aws ec2 create-route-table \
    --vpc-id $VPC_ID \
    --tag-specifications 'ResourceType=route-table,Tags=[{Key=Name,Value=csv-batch-public-rt}]' \
    --query 'RouteTable.RouteTableId' --output text)

# インターネットゲートウェイへのルート追加
aws ec2 create-route \
    --route-table-id $ROUTE_TABLE_ID \
    --destination-cidr-block 0.0.0.0/0 \
    --gateway-id $IGW_ID

# サブネットとルートテーブルの関連付け
aws ec2 associate-route-table \
    --subnet-id $PUBLIC_SUBNET_A \
    --route-table-id $ROUTE_TABLE_ID

aws ec2 associate-route-table \
    --subnet-id $PUBLIC_SUBNET_B \
    --route-table-id $ROUTE_TABLE_ID
```

### 3.2 セキュリティグループ作成
```bash
# ECSタスク用セキュリティグループ
ECS_SG=$(aws ec2 create-security-group \
    --group-name csv-batch-ecs-sg \
    --description "Security group for CSV Batch ECS tasks" \
    --vpc-id $VPC_ID \
    --tag-specifications 'ResourceType=security-group,Tags=[{Key=Name,Value=csv-batch-ecs-sg}]' \
    --query 'GroupId' --output text)

# RDS用セキュリティグループ
RDS_SG=$(aws ec2 create-security-group \
    --group-name csv-batch-rds-sg \
    --description "Security group for CSV Batch RDS" \
    --vpc-id $VPC_ID \
    --tag-specifications 'ResourceType=security-group,Tags=[{Key=Name,Value=csv-batch-rds-sg}]' \
    --query 'GroupId' --output text)

# ECSからRDSへのアクセス許可
aws ec2 authorize-security-group-ingress \
    --group-id $RDS_SG \
    --protocol tcp \
    --port 1521 \
    --source-group $ECS_SG

# ECSタスク間通信許可
aws ec2 authorize-security-group-ingress \
    --group-id $ECS_SG \
    --protocol tcp \
    --port 8080 \
    --source-group $ECS_SG

# HTTPS アウトバウンド許可
aws ec2 authorize-security-group-egress \
    --group-id $ECS_SG \
    --protocol tcp \
    --port 443 \
    --cidr 0.0.0.0/0
```

## 4. Amazon RDS (Oracle) 構築

### 4.1 RDS サブネットグループ作成
```bash
aws rds create-db-subnet-group \
    --db-subnet-group-name csv-batch-subnet-group \
    --db-subnet-group-description "Subnet group for CSV Batch Oracle DB" \
    --subnet-ids $PRIVATE_SUBNET_A $PRIVATE_SUBNET_B \
    --tags Key=Name,Value=csv-batch-subnet-group
```

### 4.2 RDS パラメータグループ作成
```bash
# Oracle用パラメータグループ作成
aws rds create-db-parameter-group \
    --db-parameter-group-name csv-batch-oracle-params \
    --db-parameter-group-family oracle-ee-19 \
    --description "Parameter group for CSV Batch Oracle"

# 必要に応じてパラメータ設定
aws rds modify-db-parameter-group \
    --db-parameter-group-name csv-batch-oracle-params \
    --parameters "ParameterName=shared_pool_size,ParameterValue=256M,ApplyMethod=pending-reboot"
```

### 4.3 RDS インスタンス作成
```bash
aws rds create-db-instance \
    --db-instance-identifier csv-batch-oracle \
    --db-instance-class db.t3.small \
    --engine oracle-ee \
    --engine-version 19.0.0.0.ru-2023-10.rur-2023-10.r1 \
    --master-username admin \
    --master-user-password 'YourSecurePassword123!' \
    --allocated-storage 20 \
    --vpc-security-group-ids $RDS_SG \
    --db-subnet-group-name csv-batch-subnet-group \
    --db-parameter-group-name csv-batch-oracle-params \
    --backup-retention-period 7 \
    --storage-encrypted \
    --no-deletion-protection \
    --tags Key=Name,Value=csv-batch-oracle

# RDS作成完了まで待機
aws rds wait db-instance-available --db-instance-identifier csv-batch-oracle

# エンドポイント取得
RDS_ENDPOINT=$(aws rds describe-db-instances \
    --db-instance-identifier csv-batch-oracle \
    --query 'DBInstances[0].Endpoint.Address' --output text)

echo "RDS Endpoint: $RDS_ENDPOINT"
```

## 5. Amazon ECR セットアップ

### 5.1 ECR リポジトリ作成
```bash
# メインアプリケーション用リポジトリ
aws ecr create-repository \
    --repository-name csv-batch-processor \
    --image-scanning-configuration scanOnPush=true

# SOAPスタブ用リポジトリ
aws ecr create-repository \
    --repository-name soap-stub \
    --image-scanning-configuration scanOnPush=true

# ECRログイン
aws ecr get-login-password --region ap-northeast-1 | \
    docker login --username AWS --password-stdin $(aws sts get-caller-identity --query Account --output text).dkr.ecr.ap-northeast-1.amazonaws.com
```

### 5.2 Docker イメージビルド・プッシュ
```bash
# メインアプリケーションのビルド・プッシュ
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REGISTRY=$ACCOUNT_ID.dkr.ecr.ap-northeast-1.amazonaws.com

# メインアプリケーション
docker buildx build --platform linux/amd64 \
    -t $ECR_REGISTRY/csv-batch-processor:latest .
docker push $ECR_REGISTRY/csv-batch-processor:latest

# SOAPスタブ
docker buildx build --platform linux/amd64 \
    -t $ECR_REGISTRY/soap-stub:latest ./soap-stub
docker push $ECR_REGISTRY/soap-stub:latest
```

## 6. Amazon S3 セットアップ

### 6.1 S3 バケット作成（自動化対応）
アプリケーションにS3バケット自動作成機能が搭載されているため、手動作成は**オプション**です。

#### 6.1.1 自動作成機能の仕組み
```java
// S3ClientService.java - @PostConstruct で自動実行
@PostConstruct
public void initializeS3Bucket() {
    try {
        if (!bucketExists()) {
            logger.info("S3 bucket '{}' does not exist. Creating bucket...", bucketName);
            createBucket();
            logger.info("S3 bucket '{}' created successfully", bucketName);
        } else {
            logger.info("S3 bucket '{}' already exists", bucketName);
        }
    } catch (Exception e) {
        logger.error("Failed to initialize S3 bucket '{}'...", bucketName, e.getMessage());
        // エラーが発生してもアプリケーションの起動は続行
    }
}
```

#### 6.1.2 手動でのS3バケット作成（従来方式）
```bash
# CSV出力用バケット作成（手動）
BUCKET_NAME="csv-batch-output-$(date +%s)"
aws s3 mb s3://$BUCKET_NAME --region ap-northeast-1

# バケットポリシー設定
cat > bucket-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "ECSTaskAccess",
            "Effect": "Allow",
            "Principal": {
                "AWS": "arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole"
            },
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:CreateBucket",
                "s3:HeadBucket"
            ],
            "Resource": [
                "arn:aws:s3:::$BUCKET_NAME",
                "arn:aws:s3:::$BUCKET_NAME/*"
            ]
        }
    ]
}
EOF

aws s3api put-bucket-policy \
    --bucket $BUCKET_NAME \
    --policy file://bucket-policy.json
```

#### 6.1.3 自動作成のメリット
- **運用負荷軽減**: 手動でのバケット作成・管理作業が不要
- **エラー耐性**: バケット未存在エラーの自動解決
- **デプロイ簡素化**: インフラ構築とアプリケーション展開の分離
- **環境間整合性**: 開発・ステージング・本番環境での統一動作

## 7. IAM ロール作成

### 7.1 ECS タスク実行ロール
```bash
# 信頼ポリシー作成
cat > ecs-trust-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "ecs-tasks.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
EOF

# ECSタスク実行ロール作成
aws iam create-role \
    --role-name ecsTaskExecutionRole \
    --assume-role-policy-document file://ecs-trust-policy.json

# 管理ポリシーをアタッチ
aws iam attach-role-policy \
    --role-name ecsTaskExecutionRole \
    --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy

# カスタムポリシー作成（S3アクセス用 - 自動バケット作成対応）
cat > s3-access-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:ListBucket",
                "s3:CreateBucket",
                "s3:HeadBucket"
            ],
            "Resource": [
                "arn:aws:s3:::$BUCKET_NAME",
                "arn:aws:s3:::$BUCKET_NAME/*"
            ]
        }
    ]
}
EOF

aws iam create-policy \
    --policy-name CsvBatchS3AccessPolicy \
    --policy-document file://s3-access-policy.json

aws iam attach-role-policy \
    --role-name ecsTaskExecutionRole \
    --policy-arn arn:aws:iam::$ACCOUNT_ID:policy/CsvBatchS3AccessPolicy
```

## 8. Amazon ECS クラスター構築

### 8.1 ECS クラスター作成
```bash
aws ecs create-cluster \
    --cluster-name csv-batch-cluster \
    --capacity-providers FARGATE \
    --default-capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
    --tags key=Name,value=csv-batch-cluster
```

### 8.2 タスク定義作成

#### 8.2.1 SOAPスタブ タスク定義
```bash
cat > soap-stub-task-definition.json << EOF
{
    "family": "soap-stub-task",
    "networkMode": "awsvpc",
    "requiresCompatibilities": ["FARGATE"],
    "cpu": "256",
    "memory": "512",
    "executionRoleArn": "arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole",
    "containerDefinitions": [
        {
            "name": "soap-stub",
            "image": "$ECR_REGISTRY/soap-stub:latest",
            "portMappings": [
                {
                    "containerPort": 8080,
                    "protocol": "tcp"
                }
            ],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "/ecs/soap-stub",
                    "awslogs-region": "ap-northeast-1",
                    "awslogs-stream-prefix": "ecs"
                }
            },
            "healthCheck": {
                "command": ["CMD-SHELL", "curl -f http://localhost:8080/ws/employees.wsdl || exit 1"],
                "interval": 30,
                "timeout": 5,
                "retries": 3
            }
        }
    ]
}
EOF

# ロググループ作成
aws logs create-log-group --log-group-name /ecs/soap-stub

# タスク定義登録
aws ecs register-task-definition \
    --cli-input-json file://soap-stub-task-definition.json
```

#### 8.2.2 メインアプリケーション タスク定義（HikariCP最適化・S3自動作成対応）
```bash
cat > csv-batch-task-definition.json << EOF
{
    "family": "csv-batch-processor-task",
    "networkMode": "awsvpc",
    "requiresCompatibilities": ["FARGATE"],
    "cpu": "1024",
    "memory": "2048",
    "executionRoleArn": "arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole",
    "taskRoleArn": "arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole",
    "containerDefinitions": [
        {
            "name": "csv-batch-processor",
            "image": "$ECR_REGISTRY/csv-batch-processor:latest",
            "environment": [
                {
                    "name": "SPRING_PROFILES_ACTIVE",
                    "value": "aws,production"
                },
                {
                    "name": "DB_URL",
                    "value": "jdbc:oracle:thin:@$RDS_ENDPOINT:1521/ORCL"
                },
                {
                    "name": "DB_USERNAME",
                    "value": "csvuser"
                },
                {
                    "name": "SOAP_API_URL",
                    "value": "http://localhost:8080/ws"
                },
                {
                    "name": "CSV_OUTPUT_PATH",
                    "value": "/tmp/result.csv"
                },
                {
                    "name": "CSV_EXPORT_S3_UPLOAD",
                    "value": "true"
                },
                {
                    "name": "CSV_EXPORT_LOCAL_BACKUP",
                    "value": "false"
                },
                {
                    "name": "AWS_S3_BUCKET",
                    "value": "$BUCKET_NAME"
                },
                {
                    "name": "AWS_DEFAULT_REGION",
                    "value": "ap-northeast-1"
                },
                {
                    "name": "SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT",
                    "value": "60000"
                },
                {
                    "name": "SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT",
                    "value": "120000"
                },
                {
                    "name": "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE",
                    "value": "5"
                },
                {
                    "name": "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE",
                    "value": "1"
                }
            ],
            "secrets": [
                {
                    "name": "DB_PASSWORD",
                    "valueFrom": "arn:aws:secretsmanager:ap-northeast-1:$ACCOUNT_ID:secret:csv-batch/db-credentials:password::"
                }
            ],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "/ecs/csv-batch-processor",
                    "awslogs-region": "ap-northeast-1",
                    "awslogs-stream-prefix": "ecs"
                }
            },
            "healthCheck": {
                "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
                "interval": 30,
                "timeout": 10,
                "retries": 3,
                "startPeriod": 120
            }
        }
    ]
}
EOF

# ロググループ作成
aws logs create-log-group --log-group-name /ecs/csv-batch-processor

# タスク定義登録
aws ecs register-task-definition \
    --cli-input-json file://csv-batch-task-definition.json
```

### 8.3 ECS サービス作成

#### 8.3.1 SOAPスタブ サービス
```bash
aws ecs create-service \
    --cluster csv-batch-cluster \
    --service-name soap-stub-service \
    --task-definition soap-stub-task:1 \
    --desired-count 1 \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$PRIVATE_SUBNET_A,$PRIVATE_SUBNET_B],securityGroups=[$ECS_SG],assignPublicIp=ENABLED}"
```

## 9. バッチジョブ実行設定

### 9.1 EventBridge ルール作成（定期実行）
```bash
# 毎日午前2時に実行するルール
aws events put-rule \
    --name csv-batch-schedule \
    --schedule-expression "cron(0 2 * * ? *)" \
    --description "Daily CSV batch processing" \
    --state ENABLED

# ECSタスクをターゲットに設定
cat > ecs-target.json << EOF
{
    "Id": "1",
    "Arn": "arn:aws:ecs:ap-northeast-1:$ACCOUNT_ID:cluster/csv-batch-cluster",
    "RoleArn": "arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole",
    "EcsParameters": {
        "TaskDefinitionArn": "arn:aws:ecs:ap-northeast-1:$ACCOUNT_ID:task-definition/csv-batch-processor-task:1",
        "LaunchType": "FARGATE",
        "NetworkConfiguration": {
            "awsvpcConfiguration": {
                "Subnets": ["$PRIVATE_SUBNET_A", "$PRIVATE_SUBNET_B"],
                "SecurityGroups": ["$ECS_SG"],
                "AssignPublicIp": "ENABLED"
            }
        }
    }
}
EOF

aws events put-targets \
    --rule csv-batch-schedule \
    --targets file://ecs-target.json
```

### 9.2 手動実行
```bash
# バッチジョブの手動実行
aws ecs run-task \
    --cluster csv-batch-cluster \
    --task-definition csv-batch-processor-task:1 \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$PRIVATE_SUBNET_A,$PRIVATE_SUBNET_B],securityGroups=[$ECS_SG],assignPublicIp=ENABLED}"
```

## 10. 監視・ログ設定

### 10.1 CloudWatch アラーム設定
```bash
# タスク失敗アラーム
aws cloudwatch put-metric-alarm \
    --alarm-name "CSV-Batch-Task-Failures" \
    --alarm-description "Alert when CSV batch task fails" \
    --metric-name RunningCount \
    --namespace AWS/ECS \
    --statistic Average \
    --period 300 \
    --threshold 0 \
    --comparison-operator LessThanThreshold \
    --dimensions Name=ServiceName,Value=csv-batch-processor-service Name=ClusterName,Value=csv-batch-cluster \
    --evaluation-periods 1

# RDS接続アラーム
aws cloudwatch put-metric-alarm \
    --alarm-name "RDS-High-Connections" \
    --alarm-description "Alert when RDS connections are high" \
    --metric-name DatabaseConnections \
    --namespace AWS/RDS \
    --statistic Average \
    --period 300 \
    --threshold 80 \
    --comparison-operator GreaterThanThreshold \
    --dimensions Name=DBInstanceIdentifier,Value=csv-batch-oracle \
    --evaluation-periods 2
```

### 10.2 カスタムメトリクス設定
```bash
# CSV処理件数メトリクス送信用IAMポリシー
cat > cloudwatch-metrics-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "cloudwatch:PutMetricData"
            ],
            "Resource": "*"
        }
    ]
}
EOF

aws iam create-policy \
    --policy-name CloudWatchMetricsPolicy \
    --policy-document file://cloudwatch-metrics-policy.json

aws iam attach-role-policy \
    --role-name ecsTaskExecutionRole \
    --policy-arn arn:aws:iam::$ACCOUNT_ID:policy/CloudWatchMetricsPolicy
```

## 11. セキュリティ設定

### 11.1 データベース暗号化
```bash
# RDS暗号化有効化（作成時に設定済み）
# 既存インスタンスの場合はスナップショットから暗号化されたインスタンスを作成
```

### 11.2 VPC エンドポイント設定
```bash
# S3 VPCエンドポイント作成
aws ec2 create-vpc-endpoint \
    --vpc-id $VPC_ID \
    --service-name com.amazonaws.ap-northeast-1.s3 \
    --route-table-ids $ROUTE_TABLE_ID

# ECR VPCエンドポイント作成
aws ec2 create-vpc-endpoint \
    --vpc-id $VPC_ID \
    --service-name com.amazonaws.ap-northeast-1.ecr.dkr \
    --vpc-endpoint-type Interface \
    --subnet-ids $PRIVATE_SUBNET_A $PRIVATE_SUBNET_B \
    --security-group-ids $ECS_SG
```

### 11.3 Secrets Manager 設定
```bash
# データベース認証情報をSecrets Managerに保存
aws secretsmanager create-secret \
    --name csv-batch/db-credentials \
    --description "Database credentials for CSV batch processor" \
    --secret-string '{"username":"csvuser","password":"YourSecurePassword123!"}'

# IAMポリシーでSecrets Managerアクセス権限を追加
cat > secrets-manager-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "secretsmanager:GetSecretValue"
            ],
            "Resource": "arn:aws:secretsmanager:ap-northeast-1:$ACCOUNT_ID:secret:csv-batch/db-credentials*"
        }
    ]
}
EOF

aws iam create-policy \
    --policy-name SecretsManagerAccessPolicy \
    --policy-document file://secrets-manager-policy.json

aws iam attach-role-policy \
    --role-name ecsTaskExecutionRole \
    --policy-arn arn:aws:iam::$ACCOUNT_ID:policy/SecretsManagerAccessPolicy
```

## 12. トラブルシューティング

### 12.1 よくある問題

#### 12.1.1 ECSタスクが起動しない
```bash
# タスクの詳細確認
aws ecs describe-tasks \
    --cluster csv-batch-cluster \
    --tasks $(aws ecs list-tasks --cluster csv-batch-cluster --query 'taskArns[0]' --output text)

# ログ確認
aws logs get-log-events \
    --log-group-name /ecs/csv-batch-processor \
    --log-stream-name ecs/csv-batch-processor/$(date +%Y/%m/%d)
```

#### 12.1.2 RDS接続エラー
```bash
# セキュリティグループ確認
aws ec2 describe-security-groups --group-ids $RDS_SG

# RDS状態確認
aws rds describe-db-instances --db-instance-identifier csv-batch-oracle
```

#### 12.1.3 S3アクセスエラー・バケット自動作成問題
```bash
# IAMロール権限確認（バケット作成権限含む）
aws iam get-role-policy \
    --role-name ecsTaskExecutionRole \
    --policy-name CsvBatchS3AccessPolicy

# バケット存在確認
aws s3 ls s3://$BUCKET_NAME

# S3バケット自動作成ログ確認（重要）
aws logs filter-log-events \
    --log-group-name /ecs/csv-batch-processor \
    --filter-pattern "S3 bucket"

# 期待される出力例:
# "S3 bucket 'csv-batch-output-xxx' created successfully"
# または "S3 bucket 'csv-batch-output-xxx' already exists"

# S3アップロード処理確認
aws logs filter-log-events \
    --log-group-name /ecs/csv-batch-processor \
    --filter-pattern "S3.*upload"

# バケット作成エラー確認
aws logs filter-log-events \
    --log-group-name /ecs/csv-batch-processor \
    --filter-pattern "Failed to.*bucket"

# 権限不足の場合の対応
# s3:CreateBucket, s3:HeadBucket 権限が付与されているか確認
aws iam get-policy-version \
    --policy-arn arn:aws:iam::$ACCOUNT_ID:policy/CsvBatchS3AccessPolicy \
    --version-id v1
```

#### 12.1.4 Secrets Manager アクセスエラー
```bash
# Secrets Manager権限確認
aws iam list-attached-role-policies --role-name ecsTaskExecutionRole

# シークレット値取得テスト
aws secretsmanager get-secret-value \
    --secret-id csv-batch/db-credentials \
    --query SecretString --output text

# ECSタスクログでSecretsエラー確認
aws logs filter-log-events \
    --log-group-name /ecs/csv-batch-processor \
    --filter-pattern "SECRET"
```

#### 12.1.5 Spring Retry・サーキットブレーカーエラー
```bash
# リトライ・サーキットブレーカー関連ログ確認
aws logs filter-log-events \
    --log-group-name /ecs/csv-batch-processor \
    --filter-pattern "Retry|Circuit|Fallback"

# SOAP API接続エラーログ確認
aws logs filter-log-events \
    --log-group-name /ecs/csv-batch-processor \
    --filter-pattern "SOAP|WebService"
```

### 12.2 デバッグコマンド
```bash
# 全リソース状態確認スクリプト
cat > check-aws-resources.sh << 'EOF'
#!/bin/bash
echo "=== VPC Status ==="
aws ec2 describe-vpcs --filters "Name=tag:Name,Values=csv-batch-vpc"

echo "=== RDS Status ==="
aws rds describe-db-instances --db-instance-identifier csv-batch-oracle

echo "=== ECS Cluster Status ==="
aws ecs describe-clusters --clusters csv-batch-cluster

echo "=== ECS Services Status ==="
aws ecs describe-services --cluster csv-batch-cluster --services soap-stub-service

echo "=== Recent Tasks ==="
aws ecs list-tasks --cluster csv-batch-cluster

echo "=== S3 Bucket Status ==="
aws s3 ls s3://$BUCKET_NAME

echo "=== Secrets Manager Status ==="
aws secretsmanager describe-secret --secret-id csv-batch/db-credentials

echo "=== IAM Role Policies ==="
aws iam list-attached-role-policies --role-name ecsTaskExecutionRole

echo "=== Recent Application Logs ==="
aws logs describe-log-streams \
    --log-group-name /ecs/csv-batch-processor \
    --order-by LastEventTime \
    --descending \
    --max-items 5
EOF

chmod +x check-aws-resources.sh
./check-aws-resources.sh
```

## 13. コスト最適化

### 13.1 コスト監視
```bash
# 予算アラート設定
aws budgets create-budget \
    --account-id $ACCOUNT_ID \
    --budget '{
        "BudgetName": "CSV-Batch-Monthly-Budget",
        "BudgetLimit": {"Amount": "100", "Unit": "USD"},
        "TimeUnit": "MONTHLY",
        "BudgetType": "COST"
    }' \
    --notifications-with-subscribers '{
        "Notification": {
            "NotificationType": "ACTUAL",
            "ComparisonOperator": "GREATER_THAN",
            "Threshold": 80
        },
        "Subscribers": [{
            "SubscriptionType": "EMAIL",
            "Address": "your-email@example.com"
        }]
    }'
```

### 13.2 リソース最適化
```bash
# 使用していない場合のリソース停止
# RDS停止（夜間・週末）
aws rds stop-db-instance --db-instance-identifier csv-batch-oracle

# RDS開始
aws rds start-db-instance --db-instance-identifier csv-batch-oracle
```

## 14. クリーンアップ

### 14.1 全リソース削除
```bash
# ECSサービス・タスク削除
aws ecs update-service \
    --cluster csv-batch-cluster \
    --service soap-stub-service \
    --desired-count 0

aws ecs delete-service \
    --cluster csv-batch-cluster \
    --service soap-stub-service

aws ecs delete-cluster --cluster csv-batch-cluster

# RDS削除
aws rds delete-db-instance \
    --db-instance-identifier csv-batch-oracle \
    --skip-final-snapshot

# S3バケット削除
aws s3 rm s3://$BUCKET_NAME --recursive
aws s3 rb s3://$BUCKET_NAME

# ECRリポジトリ削除
aws ecr delete-repository \
    --repository-name csv-batch-processor \
    --force

aws ecr delete-repository \
    --repository-name soap-stub \
    --force

# VPCとネットワークリソース削除
aws ec2 delete-vpc --vpc-id $VPC_ID
```

## 15. 運用ベストプラクティス

### 15.1 定期メンテナンス
- RDSバックアップの定期確認
- CloudWatchログの保持期間設定
- 不要なECRイメージの削除
- セキュリティパッチの適用

### 15.2 災害復旧
- 別リージョンへのデータベーススナップショット複製
- インフラストラクチャのコード化（Terraform/CloudFormation）
- 定期的な復旧テストの実施

### 15.3 スケーリング
- ECSタスクの自動スケーリング設定
- RDS読み取りレプリカの追加
- S3のライフサイクルポリシー設定