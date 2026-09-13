resource "aws_dynamodb_table" "this" {
  name         = var.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"

  attribute {
    name = "pk"
    type = "S"
  }

  attribute {
    name = "sk"
    type = "S"
  }

  attribute {
    name = "userDeckIndexGsiPk"
    type = "S"
  }

  attribute {
    name = "userDeckIndexGsiSk"
    type = "S"
  }

  attribute {
    name = "userNoteIndexGsiPk"
    type = "S"
  }

  attribute {
    name = "userNoteIndexGsiSk"
    type = "S"
  }

  attribute {
    name = "userAnalysisIndexGsiPk"
    type = "S"
  }

  attribute {
    name = "userAnalysisIndexGsiSk"
    type = "S"
  }

  attribute {
    name = "statusBulkFormatIndexGsiPk"
    type = "S"
  }

  attribute {
    name = "statusBulkFormatIndexGsiSk"
    type = "S"
  }

  global_secondary_index {
    name            = "UserDeckIndex"
    hash_key        = "userDeckIndexGsiPk"
    range_key       = "userDeckIndexGsiSk"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "UserNoteIndex"
    hash_key        = "userNoteIndexGsiPk"
    range_key       = "userNoteIndexGsiSk"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "UserAnalysisIndex"
    hash_key        = "userAnalysisIndexGsiPk"
    range_key       = "userAnalysisIndexGsiSk"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "StatusBulkFormatIndex"
    hash_key        = "statusBulkFormatIndexGsiPk"
    range_key       = "statusBulkFormatIndexGsiSk"
    projection_type = "ALL"
  }

  tags = { Name = var.table_name }
}
