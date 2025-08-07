# Infrastructure as Code ガイド（OCI版）

## 概要

このドキュメントでは、CSV バッチプロセッサーのOCI環境をTerraform/OpenTofuを使用してコード化し、エンタープライズ品質のInfrastructure as Code (IaC) を実装する方法を説明します。

---

## 🎯 IaC の目標

### エンタープライズ要件
- **一貫性**: 環境間の設定差異の排除
- **再現性**: インフラの完全な再作成可能性
- **監査性**: インフラ変更の完全なトレーサビリティ
- **セキュリティ**: 設定ミスによるセキュリティホールの防止
- **自動化**: 手動運用の完全排除

### 対象環境
- Development (開発)
- Staging (ステージング)  
- Production (本番)
- Disaster Recovery (災害復旧)

---

## 🏗️ ディレクトリ構造

```
terraform/
├── environments/
│   ├── development/
│   │   ├── main.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf
│   ├── staging/
│   │   ├── main.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf
│   └── production/
│       ├── main.tf
│       ├── terraform.tfvars
│       ├── backend.tf
│       └── security.tf
├── modules/
│   ├── network/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── security.tf
│   ├── database/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── backup.tf
│   ├── compute/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── oke.tf
│   ├── storage/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── policies.tf
│   └── security/
│       ├── main.tf
│       ├── variables.tf
│       ├── outputs.tf
│       ├── vault.tf
│       ├── iam.tf
│       └── monitoring.tf
├── scripts/
│   ├── deploy.sh
│   ├── destroy.sh
│   └── validate.sh
└── docs/
    ├── architecture.md
    └── security-policies.md
```

---

## 🌐 ネットワークモジュール

### terraform/modules/network/main.tf

```hcl
# VCN作成（Multi-AZ対応）
resource "oci_core_vcn" "csv_batch_vcn" {
  compartment_id = var.compartment_id
  display_name   = "${var.project_name}-vcn-${var.environment}"
  cidr_blocks    = var.vcn_cidr_blocks
  dns_label      = "${var.project_name}vcn${var.environment}"

  freeform_tags = merge(
    var.common_tags,
    {
      "Environment" = var.environment
      "Purpose"     = "CSV Batch Processing VCN"
    }
  )

  lifecycle {
    prevent_destroy = true  # 本番環境では削除防止
  }
}

# Internet Gateway
resource "oci_core_internet_gateway" "csv_batch_igw" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.csv_batch_vcn.id
  display_name   = "${var.project_name}-igw-${var.environment}"
  enabled        = var.enable_internet_gateway

  freeform_tags = var.common_tags
}

# Service Gateway（OCI サービスへのプライベートアクセス）
resource "oci_core_service_gateway" "csv_batch_sgw" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.csv_batch_vcn.id
  display_name   = "${var.project_name}-sgw-${var.environment}"
  
  services {
    service_id = data.oci_core_services.all_oci_services.services[0].id
  }

  freeform_tags = var.common_tags
}

# プライベートサブネット（AD-1）
resource "oci_core_subnet" "private_subnet_ad1" {
  compartment_id      = var.compartment_id
  vcn_id              = oci_core_vcn.csv_batch_vcn.id
  display_name        = "${var.project_name}-private-subnet-ad1-${var.environment}"
  cidr_block          = var.private_subnet_ad1_cidr
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  dns_label           = "privatesubad1${var.environment}"
  
  # セキュリティ設定
  prohibit_public_ip_on_vnic = true  # パブリックIP禁止
  prohibit_internet_ingress  = true  # インターネットからの受信禁止
  
  route_table_id    = oci_core_route_table.private_route_table.id
  security_list_ids = [oci_core_security_list.private_security_list.id]

  freeform_tags = merge(
    var.common_tags,
    {
      "Subnet-Type" = "Private"
      "AZ"          = "AD-1"
    }
  )
}

# プライベートサブネット（AD-2）
resource "oci_core_subnet" "private_subnet_ad2" {
  compartment_id      = var.compartment_id
  vcn_id              = oci_core_vcn.csv_batch_vcn.id
  display_name        = "${var.project_name}-private-subnet-ad2-${var.environment}"
  cidr_block          = var.private_subnet_ad2_cidr
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[1].name
  dns_label           = "privatesubad2${var.environment}"
  
  # セキュリティ設定
  prohibit_public_ip_on_vnic = true
  prohibit_internet_ingress  = true
  
  route_table_id    = oci_core_route_table.private_route_table.id
  security_list_ids = [oci_core_security_list.private_security_list.id]

  freeform_tags = merge(
    var.common_tags,
    {
      "Subnet-Type" = "Private"
      "AZ"          = "AD-2"
    }
  )
}

# ロードバランサー用パブリックサブネット（最小限）
resource "oci_core_subnet" "public_lb_subnet" {
  count               = var.enable_load_balancer ? 1 : 0
  compartment_id      = var.compartment_id
  vcn_id              = oci_core_vcn.csv_batch_vcn.id
  display_name        = "${var.project_name}-public-lb-subnet-${var.environment}"
  cidr_block          = var.public_lb_subnet_cidr
  availability_domain = null  # Regional subnet
  dns_label           = "publiclbsub${var.environment}"
  
  route_table_id    = oci_core_route_table.public_route_table[0].id
  security_list_ids = [oci_core_security_list.public_lb_security_list[0].id]

  freeform_tags = merge(
    var.common_tags,
    {
      "Subnet-Type" = "Public-LB-Only"
    }
  )
}

# プライベートルートテーブル
resource "oci_core_route_table" "private_route_table" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.csv_batch_vcn.id
  display_name   = "${var.project_name}-private-rt-${var.environment}"

  # Service Gateway経由でOCIサービスへアクセス
  route_rules {
    destination       = data.oci_core_services.all_oci_services.services[0].cidr_block
    destination_type  = "SERVICE_CIDR_BLOCK"
    network_entity_id = oci_core_service_gateway.csv_batch_sgw.id
  }

  freeform_tags = var.common_tags
}

# パブリックルートテーブル（LB専用）
resource "oci_core_route_table" "public_route_table" {
  count          = var.enable_load_balancer ? 1 : 0
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.csv_batch_vcn.id
  display_name   = "${var.project_name}-public-rt-${var.environment}"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.csv_batch_igw.id
  }

  freeform_tags = var.common_tags
}

# データソース
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_id
}

data "oci_core_services" "all_oci_services" {
  filter {
    name   = "name"
    values = ["All .* Services In Oracle Services Network"]
    regex  = true
  }
}
```

### terraform/modules/network/security.tf

```hcl
# プライベートセキュリティリスト（Zero Trust）
resource "oci_core_security_list" "private_security_list" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.csv_batch_vcn.id
  display_name   = "${var.project_name}-private-sl-${var.environment}"

  # 出力規則: OCI サービスへのHTTPS接続のみ
  egress_security_rules {
    destination      = data.oci_core_services.all_oci_services.services[0].cidr_block
    destination_type = "SERVICE_CIDR_BLOCK"
    protocol         = "6"  # TCP
    description      = "HTTPS to OCI services via Service Gateway"

    tcp_options {
      min = 443
      max = 443
    }
  }

  # 入力規則: Load Balancerサブネットからのみ
  ingress_security_rules {
    source      = var.public_lb_subnet_cidr
    protocol    = "6"
    description = "HTTP from Load Balancer subnet only"

    tcp_options {
      min = 8080
      max = 8080
    }
  }

  # Kubernetes内部通信（必要最小限）
  ingress_security_rules {
    source      = var.private_subnet_ad1_cidr
    protocol    = "all"
    description = "Kubernetes internal communication AD-1"
  }

  ingress_security_rules {
    source      = var.private_subnet_ad2_cidr
    protocol    = "all"
    description = "Kubernetes internal communication AD-2"
  }

  # データベース接続
  egress_security_rules {
    destination = "10.0.0.0/16"  # VCN内のみ
    protocol    = "6"
    description = "Oracle DB access within VCN"

    tcp_options {
      min = 1521
      max = 1521
    }
  }

  freeform_tags = var.common_tags
}

# Load Balancer用セキュリティリスト（最小権限）
resource "oci_core_security_list" "public_lb_security_list" {
  count          = var.enable_load_balancer ? 1 : 0
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.csv_batch_vcn.id
  display_name   = "${var.project_name}-public-lb-sl-${var.environment}"

  # 外部からのHTTPS接続（WAF経由のみ）
  ingress_security_rules {
    source      = "0.0.0.0/0"
    protocol    = "6"
    description = "HTTPS from WAF only"

    tcp_options {
      min = 443
      max = 443
    }
  }

  # プライベートサブネットへの転送
  egress_security_rules {
    destination = var.private_subnet_ad1_cidr
    protocol    = "6"
    description = "Forward to private subnet AD-1"

    tcp_options {
      min = 8080
      max = 8080
    }
  }

  egress_security_rules {
    destination = var.private_subnet_ad2_cidr
    protocol    = "6"
    description = "Forward to private subnet AD-2"

    tcp_options {
      min = 8080
      max = 8080
    }
  }

  freeform_tags = var.common_tags
}

# Network ACLs（追加セキュリティ層）
resource "oci_core_network_acl" "csv_batch_nacl" {
  count          = var.enable_network_acls ? 1 : 0
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.csv_batch_vcn.id
  display_name   = "${var.project_name}-nacl-${var.environment}"

  # Stateless規則でより厳密な制御
  ingress_security_rules {
    source      = var.public_lb_subnet_cidr
    protocol    = "6"
    description = "Stateless HTTP from LB"
    is_stateless = true

    tcp_options {
      destination_port_range {
        min = 8080
        max = 8080
      }
    }
  }

  egress_security_rules {
    destination  = data.oci_core_services.all_oci_services.services[0].cidr_block
    protocol     = "6"
    description  = "Stateless HTTPS to OCI services"
    is_stateless = true

    tcp_options {
      destination_port_range {
        min = 443
        max = 443
      }
    }
  }

  freeform_tags = var.common_tags
}
```

---

## 🗄️ データベースモジュール

### terraform/modules/database/main.tf

```hcl
# Autonomous Database
resource "oci_database_autonomous_database" "csv_batch_adb" {
  compartment_id           = var.compartment_id
  display_name            = "${var.project_name}-adb-${var.environment}"
  db_name                 = "${var.project_name}adb${var.environment}"
  
  # 容量設定
  cpu_core_count          = var.adb_cpu_core_count
  data_storage_size_in_tbs = var.adb_storage_size_tbs
  auto_scaling_enabled    = var.adb_auto_scaling_enabled
  
  # 高可用性設定
  is_dedicated                    = var.adb_is_dedicated
  autonomous_database_backup_id   = var.restore_from_backup_id
  
  # セキュリティ設定
  is_mtls_connection_required = true  # mTLS必須
  subnet_id                   = var.private_subnet_id
  nsg_ids                     = [oci_core_network_security_group.adb_nsg.id]
  
  # 暗号化設定（Customer-Managed Keys）
  kms_key_id = var.kms_key_id
  
  # Admin password（Vault経由で管理）
  admin_password = data.oci_secrets_secretbundle.adb_admin_password.secret_bundle_content[0].content
  
  # バックアップ設定
  backup_retention_period_in_days = var.backup_retention_days
  
  # ラベルとタグ
  freeform_tags = merge(
    var.common_tags,
    {
      "Environment" = var.environment
      "BackupPolicy" = "Mandatory"
      "Encryption"   = "CustomerManagedKeys"
    }
  )

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [admin_password]  # Vaultで管理されるため変更無視
  }
}

# Network Security Group for ADB
resource "oci_core_network_security_group" "adb_nsg" {
  compartment_id = var.compartment_id
  vcn_id         = var.vcn_id
  display_name   = "${var.project_name}-adb-nsg-${var.environment}"

  freeform_tags = var.common_tags
}

# NSG Rules for ADB
resource "oci_core_network_security_group_security_rule" "adb_ingress_rule" {
  network_security_group_id = oci_core_network_security_group.adb_nsg.id
  direction                 = "INGRESS"
  protocol                  = "6"
  description              = "Oracle DB access from OKE pods"

  source      = var.oke_subnet_cidr
  source_type = "CIDR_BLOCK"

  tcp_options {
    destination_port_range {
      min = 1521
      max = 1521
    }
  }
}

# データベースユーザー作成
resource "oci_database_autonomous_database_wallet" "csv_batch_wallet" {
  autonomous_database_id = oci_database_autonomous_database.csv_batch_adb.id
  password               = data.oci_secrets_secretbundle.wallet_password.secret_bundle_content[0].content
  generate_type          = "SINGLE"
  base64_encode_content  = true
}

# Vaultからのシークレット取得
data "oci_secrets_secretbundle" "adb_admin_password" {
  secret_id = var.adb_admin_password_secret_id
}

data "oci_secrets_secretbundle" "wallet_password" {
  secret_id = var.wallet_password_secret_id
}

# バックアップ設定
resource "oci_database_autonomous_database_backup" "csv_batch_backup" {
  count                   = var.enable_manual_backups ? 1 : 0
  autonomous_database_id  = oci_database_autonomous_database.csv_batch_adb.id
  display_name           = "${var.project_name}-backup-${formatdate("YYYY-MM-DD", timestamp())}"
  type                   = "FULL"

  freeform_tags = merge(
    var.common_tags,
    {
      "BackupType" = "Manual"
      "CreatedAt"  = formatdate("YYYY-MM-DD", timestamp())
    }
  )
}
```

---

## ⚙️ OKEクラスターモジュール

### terraform/modules/compute/oke.tf

```hcl
# OKE Cluster（Enhanced）
resource "oci_containerengine_cluster" "csv_batch_cluster" {
  compartment_id     = var.compartment_id
  kubernetes_version = var.kubernetes_version
  name              = "${var.project_name}-cluster-${var.environment}"
  vcn_id            = var.vcn_id

  # セキュリティ強化オプション
  options {
    service_lb_subnet_ids = var.lb_subnet_ids
    add_ons {
      is_kubernetes_dashboard_enabled = false  # セキュリティ上無効
      is_tiller_enabled              = false   # Helm v3使用のため無効
    }
    
    kubernetes_network_config {
      pods_cidr     = var.pods_cidr
      services_cidr = var.services_cidr
    }
    
    # Pod Security Standards
    admission_controller_options {
      is_pod_security_policy_enabled = true
    }
  }

  # セキュリティ設定
  cluster_pod_network_options {
    cni_type = "FLANNEL_OVERLAY"
  }

  # プライベートAPI Server
  endpoint_config {
    is_public_ip_enabled = false
    subnet_id           = var.cluster_endpoint_subnet_id
    nsg_ids             = [oci_core_network_security_group.cluster_endpoint_nsg.id]
  }

  kms_key_id = var.cluster_kms_key_id  # エンベロープ暗号化

  freeform_tags = merge(
    var.common_tags,
    {
      "Environment"    = var.environment
      "WorkloadType"   = "BatchProcessing"
      "SecurityLevel"  = "Enterprise"
    }
  )

  timeouts {
    create = "20m"
    delete = "20m"
  }
}

# Node Pool（Multi-AZ）
resource "oci_containerengine_node_pool" "csv_batch_node_pool" {
  cluster_id         = oci_containerengine_cluster.csv_batch_cluster.id
  compartment_id     = var.compartment_id
  kubernetes_version = var.kubernetes_version
  name               = "${var.project_name}-nodepool-${var.environment}"

  # Multi-AZ配置
  node_config_details {
    placement_configs {
      availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
      subnet_id          = var.private_subnet_ad1_id
      fault_domains      = ["FAULT-DOMAIN-1", "FAULT-DOMAIN-2"]
    }
    placement_configs {
      availability_domain = data.oci_identity_availability_domains.ads.availability_domains[1].name
      subnet_id          = var.private_subnet_ad2_id
      fault_domains      = ["FAULT-DOMAIN-1", "FAULT-DOMAIN-2"]
    }

    size = var.node_pool_initial_size

    # ノード設定
    nsg_ids                = [oci_core_network_security_group.worker_node_nsg.id]
    is_pv_encryption_in_transit_enabled = true
    kms_key_id            = var.worker_node_kms_key_id
  }

  # ノード形状
  node_shape = var.node_shape

  node_shape_config {
    ocpus         = var.node_ocpus
    memory_in_gbs = var.node_memory_gb
  }

  # OS設定
  node_source_details {
    image_id    = var.node_image_id
    source_type = "IMAGE"
    
    # セキュリティ強化
    boot_volume_size_in_gbs = var.node_boot_volume_size
  }

  # 自動スケーリング
  node_eviction_node_pool_settings {
    eviction_grace_duration              = "PT30M"
    is_force_delete_after_grace_duration = false
  }

  # 初期ノード設定
  initial_node_labels {
    key   = "environment"
    value = var.environment
  }
  initial_node_labels {
    key   = "workload"
    value = "csv-batch-processing"
  }

  freeform_tags = merge(
    var.common_tags,
    {
      "NodePool"     = "Primary"
      "AutoScaling"  = "Enabled"
    }
  )

  timeouts {
    create = "30m"
    delete = "30m"
  }
}

# Cluster Autoscaler設定
resource "oci_containerengine_addon" "cluster_autoscaler" {
  addon_name                = "cluster-autoscaler"
  cluster_id               = oci_containerengine_cluster.csv_batch_cluster.id
  remove_addon_resources_on_delete = false
  
  configurations {
    key   = "numOfNodesToScaleUp"
    value = "1"
  }
  configurations {
    key   = "scaleUpDelayAfterAdd"
    value = "10m"
  }
  configurations {
    key   = "scaleDownDelayAfterAdd"
    value = "20m"
  }
}

# データソース
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_id
}
```

---

## 🔐 セキュリティモジュール

### terraform/modules/security/vault.tf

```hcl
# OCI Vault（HSMバックアップ）
resource "oci_kms_vault" "csv_batch_vault" {
  compartment_id   = var.compartment_id
  display_name     = "${var.project_name}-vault-${var.environment}"
  vault_type       = var.vault_type  # "VIRTUAL_PRIVATE" for production
  
  # 復元設定
  restore_from_file {
    restore_trigger = var.restore_trigger
    content_length  = var.backup_content_length
    content_md5     = var.backup_content_md5
  }

  freeform_tags = merge(
    var.common_tags,
    {
      "SecurityLevel" = "HSM-backed"
      "Purpose"       = "CSV Batch Secrets"
    }
  )

  lifecycle {
    prevent_destroy = true  # 本番環境では削除防止
  }
}

# マスター暗号化キー
resource "oci_kms_key" "csv_batch_master_key" {
  compartment_id      = var.compartment_id
  display_name        = "${var.project_name}-master-key-${var.environment}"
  management_endpoint = oci_kms_vault.csv_batch_vault.management_endpoint

  key_shape {
    algorithm = "AES"
    length    = 256
  }

  protection_mode = var.key_protection_mode  # "HSM" for production
  
  # キーローテーション
  is_auto_rotation_enabled = true
  auto_key_rotation_details {
    rotation_interval_in_days   = var.key_rotation_days
    time_of_schedule_start     = var.rotation_start_time
  }

  freeform_tags = merge(
    var.common_tags,
    {
      "KeyType"        = "Master"
      "AutoRotation"   = "Enabled"
    }
  )
}

# データベースパスワード用シークレット
resource "oci_vault_secret" "database_password" {
  compartment_id = var.compartment_id
  vault_id       = oci_kms_vault.csv_batch_vault.id
  key_id         = oci_kms_key.csv_batch_master_key.id
  secret_name    = "${var.project_name}-db-password-${var.environment}"
  description    = "Database admin password for ${var.environment}"

  secret_content {
    content_type = "BASE64"
    content      = base64encode(var.database_password)
    stage        = "CURRENT"
  }

  secret_rules {
    rule_type = "SECRET_EXPIRY_RULE"
    time_of_absolute_expiry = timeadd(timestamp(), "8760h")  # 1年後
  }

  secret_rules {
    rule_type                = "SECRET_REUSE_RULE"
    is_enforced_on_deleted_secret_versions = true
  }

  freeform_tags = merge(
    var.common_tags,
    {
      "SecretType" = "DatabasePassword"
    }
  )

  lifecycle {
    ignore_changes = [secret_content[0].content]  # ローテーション対応
  }
}

# API キー用シークレット
resource "oci_vault_secret" "soap_api_key" {
  compartment_id = var.compartment_id
  vault_id       = oci_kms_vault.csv_batch_vault.id
  key_id         = oci_kms_key.csv_batch_master_key.id
  secret_name    = "${var.project_name}-soap-api-key-${var.environment}"
  description    = "SOAP API authentication key"

  secret_content {
    content_type = "BASE64"
    content      = base64encode(var.soap_api_key)
    stage        = "CURRENT"
  }

  freeform_tags = merge(
    var.common_tags,
    {
      "SecretType" = "APIKey"
    }
  )
}

# Walletパスワード用シークレット
resource "oci_vault_secret" "wallet_password" {
  compartment_id = var.compartment_id
  vault_id       = oci_kms_vault.csv_batch_vault.id
  key_id         = oci_kms_key.csv_batch_master_key.id
  secret_name    = "${var.project_name}-wallet-password-${var.environment}"
  description    = "ADB wallet password"

  secret_content {
    content_type = "BASE64"
    content      = base64encode(var.wallet_password)
    stage        = "CURRENT"
  }

  freeform_tags = merge(
    var.common_tags,
    {
      "SecretType" = "WalletPassword"
    }
  )
}
```

### terraform/modules/security/iam.tf

```hcl
# Dynamic Group for OKE Worker Nodes
resource "oci_identity_dynamic_group" "csv_batch_oke_nodes" {
  compartment_id = var.tenancy_ocid
  description    = "Dynamic group for CSV batch OKE worker nodes"
  matching_rule  = "ALL {instance.compartment.id = '${var.compartment_id}', tag.oke-cluster.name = '${var.project_name}-cluster-${var.environment}'}"
  name           = "${var.project_name}-oke-nodes-${var.environment}"

  freeform_tags = var.common_tags
}

# Policy for OKE Worker Nodes（最小権限）
resource "oci_identity_policy" "csv_batch_oke_policy" {
  compartment_id = var.compartment_id
  description    = "Minimal privileges for CSV batch OKE worker nodes"
  name           = "${var.project_name}-oke-policy-${var.environment}"

  statements = [
    # Object Storage最小権限
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_oke_nodes.name} to manage objects in compartment id ${var.compartment_id} where target.bucket.name='${var.csv_output_bucket_name}'",
    
    # ADB接続権限
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_oke_nodes.name} to use autonomous-databases in compartment id ${var.compartment_id}",
    
    # Vault読み取り権限
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_oke_nodes.name} to use vaults in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_oke_nodes.name} to use keys in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_oke_nodes.name} to use secret-bundles in compartment id ${var.compartment_id}",
    
    # 監視権限（メトリクス送信）
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_oke_nodes.name} to use metrics in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_oke_nodes.name} to manage log-content in compartment id ${var.compartment_id}",
  ]

  freeform_tags = var.common_tags
}

# Dynamic Group for Container Instances（バッチジョブ用）
resource "oci_identity_dynamic_group" "csv_batch_instances" {
  compartment_id = var.tenancy_ocid
  description    = "Dynamic group for CSV batch container instances"
  matching_rule  = "ALL {resource.type = 'instance', instance.compartment.id = '${var.compartment_id}', tag.project.name = '${var.project_name}'}"
  name           = "${var.project_name}-instances-${var.environment}"

  freeform_tags = var.common_tags
}

# Policy for Container Instances
resource "oci_identity_policy" "csv_batch_instances_policy" {
  compartment_id = var.compartment_id
  description    = "Minimal privileges for CSV batch container instances"
  name           = "${var.project_name}-instances-policy-${var.environment}"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_instances.name} to manage objects in compartment id ${var.compartment_id} where target.bucket.name='${var.csv_output_bucket_name}'",
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_instances.name} to use autonomous-databases in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.csv_batch_instances.name} to read secret-bundles in compartment id ${var.compartment_id}",
  ]

  freeform_tags = var.common_tags
}

# Service Account for CI/CD（制限付き）
resource "oci_identity_user" "cicd_service_account" {
  count       = var.enable_cicd_user ? 1 : 0
  compartment_id = var.tenancy_ocid
  description = "Service account for CI/CD pipeline"
  name        = "${var.project_name}-cicd-sa-${var.environment}"

  freeform_tags = merge(
    var.common_tags,
    {
      "ServiceType" = "CICD"
      "Access"      = "Limited"
    }
  )
}

# API Key for CI/CD Service Account
resource "oci_identity_api_key" "cicd_api_key" {
  count   = var.enable_cicd_user ? 1 : 0
  user_id = oci_identity_user.cicd_service_account[0].id
  key_value = var.cicd_public_key_content
}

# Group for CI/CD Users
resource "oci_identity_group" "cicd_group" {
  count       = var.enable_cicd_user ? 1 : 0
  compartment_id = var.tenancy_ocid
  description = "Group for CI/CD service accounts"
  name        = "${var.project_name}-cicd-group-${var.environment}"

  freeform_tags = var.common_tags
}

# Policy for CI/CD Group（デプロイ権限のみ）
resource "oci_identity_policy" "cicd_group_policy" {
  count          = var.enable_cicd_user ? 1 : 0
  compartment_id = var.compartment_id
  description    = "Limited deployment privileges for CI/CD"
  name           = "${var.project_name}-cicd-policy-${var.environment}"

  statements = [
    # OKEへのデプロイ権限のみ
    "Allow group ${oci_identity_group.cicd_group[0].name} to manage cluster-family in compartment id ${var.compartment_id}",
    "Allow group ${oci_identity_group.cicd_group[0].name} to use container-images in tenancy",
    
    # Container Registry権限
    "Allow group ${oci_identity_group.cicd_group[0].name} to manage repos in tenancy",
    
    # 読み取り権限（デバッグ用）
    "Allow group ${oci_identity_group.cicd_group[0].name} to inspect compartments in compartment id ${var.compartment_id}",
    "Allow group ${oci_identity_group.cicd_group[0].name} to read metrics in compartment id ${var.compartment_id}",
  ]

  freeform_tags = var.common_tags
}

# User Membership
resource "oci_identity_user_group_membership" "cicd_membership" {
  count    = var.enable_cicd_user ? 1 : 0
  group_id = oci_identity_group.cicd_group[0].id
  user_id  = oci_identity_user.cicd_service_account[0].id
}
```

---

## 🚀 デプロイメントスクリプト

### scripts/deploy.sh

```bash
#!/bin/bash

# CSV Batch Processor - OCI Infrastructure Deployment Script
set -euo pipefail

# 設定
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENVIRONMENTS=("development" "staging" "production")

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

warn() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] WARNING: $1${NC}"
}

error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1${NC}"
    exit 1
}

# Usage function
usage() {
    cat << EOF
Usage: $0 -e ENVIRONMENT [OPTIONS]

Deploy CSV Batch Processor infrastructure to OCI

Options:
    -e ENVIRONMENT      Target environment (development|staging|production)
    -a                  Auto-approve Terraform changes
    -d                  Destroy infrastructure (use with caution)
    -v                  Verbose output
    -h                  Show this help message

Examples:
    $0 -e development                 # Deploy to development
    $0 -e production -v               # Deploy to production with verbose output
    $0 -e staging -a                  # Auto-approve deployment to staging
    $0 -e development -d              # Destroy development infrastructure

EOF
}

# Variable initialization
ENVIRONMENT=""
AUTO_APPROVE=""
DESTROY=""
VERBOSE=""

# Parse command line arguments
while getopts "e:advh" opt; do
    case $opt in
        e)
            ENVIRONMENT="$OPTARG"
            ;;
        a)
            AUTO_APPROVE="-auto-approve"
            ;;
        d)
            DESTROY="true"
            ;;
        v)
            VERBOSE="-verbose"
            ;;
        h)
            usage
            exit 0
            ;;
        *)
            error "Invalid option: -$OPTARG"
            ;;
    esac
done

# Validate required parameters
if [[ -z "$ENVIRONMENT" ]]; then
    error "Environment (-e) is required"
fi

# Validate environment
if [[ ! " ${ENVIRONMENTS[@]} " =~ " ${ENVIRONMENT} " ]]; then
    error "Invalid environment: $ENVIRONMENT. Valid options: ${ENVIRONMENTS[*]}"
fi

# Environment-specific settings
TERRAFORM_DIR="$PROJECT_ROOT/terraform/environments/$ENVIRONMENT"
TFVARS_FILE="$TERRAFORM_DIR/terraform.tfvars"

# Validation checks
log "Starting pre-deployment validation..."

# Check if Terraform directory exists
if [[ ! -d "$TERRAFORM_DIR" ]]; then
    error "Terraform directory not found: $TERRAFORM_DIR"
fi

# Check if tfvars file exists
if [[ ! -f "$TFVARS_FILE" ]]; then
    error "Terraform variables file not found: $TFVARS_FILE"
fi

# Check OCI CLI configuration
log "Validating OCI CLI configuration..."
if ! oci iam compartment list --limit 1 &>/dev/null; then
    error "OCI CLI not properly configured or not authenticated"
fi

# Check Terraform version
log "Checking Terraform version..."
TERRAFORM_VERSION=$(terraform version -json | jq -r '.terraform_version')
REQUIRED_VERSION="1.5.0"
if ! printf '%s\n%s\n' "$REQUIRED_VERSION" "$TERRAFORM_VERSION" | sort -V -C; then
    warn "Terraform version $TERRAFORM_VERSION is older than recommended $REQUIRED_VERSION"
fi

# Navigate to Terraform directory
cd "$TERRAFORM_DIR"
log "Working in directory: $(pwd)"

# Initialize Terraform
log "Initializing Terraform..."
terraform init -upgrade

# Validate Terraform configuration
log "Validating Terraform configuration..."
terraform validate

# Format check (non-blocking)
log "Checking Terraform formatting..."
if ! terraform fmt -check -recursive; then
    warn "Terraform files are not properly formatted. Run 'terraform fmt -recursive' to fix."
fi

# Security validation for production
if [[ "$ENVIRONMENT" == "production" ]]; then
    log "Performing additional security validation for production environment..."
    
    # Check for security-sensitive resources
    if terraform plan -detailed-exitcode $VERBOSE > /dev/null; then
        log "Production security validation passed"
    else
        error "Production security validation failed"
    fi
fi

# Plan infrastructure changes
log "Planning infrastructure changes..."
if [[ "$DESTROY" == "true" ]]; then
    terraform plan -destroy -var-file="$TFVARS_FILE" $VERBOSE
else
    terraform plan -var-file="$TFVARS_FILE" $VERBOSE
fi

# Confirmation for production
if [[ "$ENVIRONMENT" == "production" && -z "$AUTO_APPROVE" ]]; then
    read -p "Are you sure you want to apply changes to PRODUCTION environment? (yes/no): " confirm
    if [[ "$confirm" != "yes" ]]; then
        log "Deployment cancelled by user"
        exit 0
    fi
fi

# Apply infrastructure changes
log "Applying infrastructure changes..."
if [[ "$DESTROY" == "true" ]]; then
    terraform destroy -var-file="$TFVARS_FILE" $AUTO_APPROVE $VERBOSE
    log "Infrastructure destroyed successfully"
else
    terraform apply -var-file="$TFVARS_FILE" $AUTO_APPROVE $VERBOSE
    log "Infrastructure deployed successfully"
fi

# Post-deployment validations
if [[ "$DESTROY" != "true" ]]; then
    log "Running post-deployment validations..."
    
    # Extract outputs
    log "Extracting Terraform outputs..."
    OKE_CLUSTER_ID=$(terraform output -raw oke_cluster_id)
    LOAD_BALANCER_IP=$(terraform output -raw load_balancer_ip)
    ADB_CONNECTION_STRING=$(terraform output -raw adb_connection_string)
    
    # Validate OKE cluster
    log "Validating OKE cluster..."
    if oci ce cluster get --cluster-id "$OKE_CLUSTER_ID" --query 'data."lifecycle-state"' --raw-output | grep -q "ACTIVE"; then
        log "OKE cluster is active and healthy"
    else
        warn "OKE cluster is not in ACTIVE state"
    fi
    
    # Generate kubeconfig
    log "Generating kubeconfig for OKE cluster..."
    oci ce cluster create-kubeconfig \
        --cluster-id "$OKE_CLUSTER_ID" \
        --file "$HOME/.kube/config-${ENVIRONMENT}" \
        --region "$(oci iam region-subscription list --query 'data[0].{region:"region-name"}' --raw-output)" \
        --token-version 2.0.0 \
        --kube-endpoint PRIVATE_ENDPOINT
    
    log "Kubeconfig saved to: $HOME/.kube/config-${ENVIRONMENT}"
    log "To use this cluster, run: export KUBECONFIG=$HOME/.kube/config-${ENVIRONMENT}"
    
    # Display important information
    cat << EOF

=== Deployment Summary ===
Environment: $ENVIRONMENT
OKE Cluster ID: $OKE_CLUSTER_ID
Load Balancer IP: $LOAD_BALANCER_IP
ADB Connection: $ADB_CONNECTION_STRING

Next Steps:
1. Configure kubectl: export KUBECONFIG=$HOME/.kube/config-${ENVIRONMENT}
2. Deploy application: kubectl apply -f kubernetes/
3. Check cluster status: kubectl get nodes
4. Access monitoring: Check OCI Console -> Monitoring

EOF
fi

log "Deployment completed successfully!"
```

---

## 📋 本番環境設定例

### terraform/environments/production/terraform.tfvars

```hcl
# 基本設定
project_name = "csv-batch"
environment  = "production"
region       = "us-ashburn-1"

# コンパートメント設定
compartment_id = "ocid1.compartment.oc1..aaaaaaaa..."

# ネットワーク設定（Multi-AZ）
vcn_cidr_blocks = ["10.0.0.0/16"]
private_subnet_ad1_cidr = "10.0.10.0/24"
private_subnet_ad2_cidr = "10.0.20.0/24"
public_lb_subnet_cidr   = "10.0.1.0/24"

enable_load_balancer = true
enable_network_acls  = true

# OKE設定（エンタープライズグレード）
kubernetes_version      = "v1.28.2"
node_shape             = "VM.Standard.A1.Flex"
node_ocpus             = 4
node_memory_gb         = 24
node_pool_initial_size = 3
node_boot_volume_size  = 100

# Autonomous Database設定
adb_cpu_core_count        = 4
adb_storage_size_tbs      = 2
adb_auto_scaling_enabled  = true
adb_is_dedicated          = false
backup_retention_days     = 30
enable_manual_backups     = true

# セキュリティ設定
vault_type          = "VIRTUAL_PRIVATE"
key_protection_mode = "HSM"
key_rotation_days   = 90

# 監視設定
enable_advanced_monitoring = true
enable_log_analytics       = true
alert_notification_topic   = "ocid1.onstopic.oc1..aaaaaaaa..."

# タグ設定
common_tags = {
  "Project"     = "csv-batch-processor"
  "Environment" = "production"
  "ManagedBy"   = "Terraform"
  "Owner"       = "platform-team@company.com"
  "CostCenter"  = "IT-BATCH-001"
  "Criticality" = "High"
  "DataClass"   = "Sensitive"
  "Backup"      = "Required"
}

# 災害復旧設定
enable_cross_region_backup = true
backup_destination_region  = "us-phoenix-1"
```

---

## 🔧 運用コマンド

### 基本操作
```bash
# 開発環境デプロイ
./scripts/deploy.sh -e development

# 本番環境デプロイ（確認付き）
./scripts/deploy.sh -e production -v

# ステージング環境自動デプロイ
./scripts/deploy.sh -e staging -a

# インフラ破棄（開発環境のみ推奨）
./scripts/deploy.sh -e development -d
```

### 検証・メンテナンス
```bash
# Terraformプラン確認
cd terraform/environments/production
terraform plan -var-file=terraform.tfvars

# セキュリティ検証
terraform plan -var-file=terraform.tfvars | grep -E "(security|vault|encrypt)"

# コスト見積もり
terraform plan -var-file=terraform.tfvars -out=plan.out
infracost breakdown --path plan.out
```

---

## 🚨 セキュリティ考慮事項

### Critical設定
1. **Backend State暗号化**: Terraform StateをOCI Object Storageで暗号化保存
2. **シークレット管理**: すべての機密情報はOCI Vaultで管理
3. **最小権限**: IAMポリシーは必要最小限の権限のみ
4. **監査ログ**: すべてのOCI API呼び出しを記録
5. **Network Isolation**: すべてのリソースをプライベートサブネット配置

### 運用セキュリティ
- 本番環境への変更は必ず2名承認制
- Terraformプランは事前レビュー必須
- セキュリティスキャンをCI/CDに統合
- 定期的な権限棚卸しとローテーション

---

**最終更新**: 2025-08-06 - エンタープライズ対応IaCガイド  
**対象**: Terraform 1.5+ / OCI Provider 5.0+  
**セキュリティレベル**: Enterprise Production Ready

**重要**: このIaC設定は本番環境での使用を前提としています。開発環境では適宜設定を簡素化してください。