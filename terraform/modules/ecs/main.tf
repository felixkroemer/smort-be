resource "aws_ecs_cluster" "this" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_iam_role" "execution" {
  name = "${var.name}-ecs-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "task" {
  name = "${var.name}-ecs-task"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "task_dynamodb" {
  name = "smort-ecs-task-dynamodb"
  role = aws_iam_role.task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "AccessDynamoDbTable"
        Effect   = "Allow"
        Action   = ["dynamodb:*"]
        Resource = [
          var.dynamodb_table_arn,
          "${var.dynamodb_table_arn}/index/*",
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy" "execution_ssm_secrets" {
  name = "smort-ecs-execution-ssm-secrets"
  role = aws_iam_role.execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadSSMParameters"
        Effect = "Allow"
        Action = ["ssm:GetParameters"]
        Resource = [
          var.base_data_dir_arn,
          var.analysis_db_directory_name_arn,
          var.analysis_max_db_size_arn,
          var.auth0_issuer_uri_arn,
          var.smort_allowed_email_arn,
          var.openai_model_arn,
        ]
      },
      {
        Sid    = "ReadSecrets"
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = [
          var.openai_api_key_arn,
          var.auth0_client_id_arn,
        ]
      },
    ]
  })
}

resource "aws_security_group" "task" {
  name   = "${var.name}-ecs-task"
  vpc_id = var.vpc_id

  ingress {
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [var.alb_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name}-ecs-task" }
}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name}"
  retention_in_days = 7

  tags = { Name = "${var.name}-ecs-logs" }
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name  = var.name
      image = var.container_image
      portMappings = [
        {
          containerPort = var.container_port
        }
      ]
      secrets = [
        { name = "BASE_DATA_DIR", valueFrom = var.base_data_dir_arn },
        { name = "ANALYSIS_DB_DIRECTORY_NAME", valueFrom = var.analysis_db_directory_name_arn },
        { name = "ANALYSIS_MAX_DB_SIZE", valueFrom = var.analysis_max_db_size_arn },
        { name = "AUTH0_ISSUER_URI", valueFrom = var.auth0_issuer_uri_arn },
        { name = "SMORT_ALLOWED_EMAIL", valueFrom = var.smort_allowed_email_arn },
        { name = "OPENAI_MODEL", valueFrom = var.openai_model_arn },
        { name = "OPENAI_API_KEY", valueFrom = var.openai_api_key_arn },
        { name = "AUTH0_CLIENT_ID", valueFrom = var.auth0_client_id_arn },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.this.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = var.name
        }
      }
    }
  ])

  tags = { Name = "${var.name}-task-definition" }
}

resource "aws_ecs_service" "this" {
  name                              = var.name
  cluster                           = aws_ecs_cluster.this.id
  task_definition                   = aws_ecs_task_definition.this.arn
  desired_count                     = 1
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 120

  network_configuration {
    subnets         = var.private_subnet_ids
    security_groups = [aws_security_group.task.id]
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = var.name
    container_port   = var.container_port
  }

  depends_on = [aws_iam_role_policy_attachment.execution]
}
