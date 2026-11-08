// 顶栏按钮：把当前课堂的考试信息整理成一段话插进对话。
function describe(exam) {
  if (!exam || !exam.questions || exam.questions.length === 0) {
    return "当前课堂还没有进行中的考试。先让 AI 出一套题，再点这个按钮就能把题目信息带进对话。";
  }
  var lines = ["本次考试：" + (exam.title || "未命名") + "（共 " + exam.questions.length + " 题）"];
  for (var i = 0; i < exam.questions.length; i++) {
    var question = exam.questions[i] || {};
    lines.push((i + 1) + ". " + (question.question || "（题干为空）"));
  }
  return lines.join("\n");
}

function onInsertExam(ctx) {
  var read = host.readExam();
  var text;
  if (read && read.ok) {
    var data = JSON.parse(read.value || "{}");
    text = describe(data.exam ? data.exam : data);
  } else {
    text = "读不到考试信息：" + ((read && read.message) || "未知原因");
  }
  var written = host.insertDialog("assistant", text);
  if (!written || !written.ok) {
    ctx.log("插入对话失败：" + ((written && written.message) || "unknown"));
  }
  host.requestUiRefresh();
}
