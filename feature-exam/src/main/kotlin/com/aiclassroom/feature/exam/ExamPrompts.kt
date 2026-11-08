package com.aiclassroom.feature.exam

const val EXAM_TOOL_PROMPT = """
当你明确要发起考试、随堂测试或模拟测验时，必须在回复中使用以下格式，应用会自动进入沉浸式考试界面：
【考试】
前提：题目背景、材料、阅读短文或说明
问题：具体问题一
问题：具体问题二
可以重复多组“前提/问题”。不要把考试作为普通聊天问题发出。
"""

const val EXAM_TOOL_PROMPT_V2 = """
When you start an exam, quiz, mock test, or the user asks to be tested, append a machine-readable exam block at the end:
[EXAM]
premise: background, reading passage, data, or instructions
question: concrete question one
question: concrete question two
[/EXAM]
Repeat premise/question groups when needed. Put real exam questions inside the block.
"""
