/**
 * 聊天页导学建议的演示候选池（仅开发 + kaoyan.insight.mock=on 时使用）
 *
 * 为什么需要它：
 * - 后端 `RecommendationEngine.generateCHAT` 目前仍是空实现（直接返回空列表），
 *   真实接口在 CHAT 场景拿不到任何候选；
 * - 前端 A 的 Mock 只准备 3 条，且按 limit 截断，无法演示"换一批 / 忽略后补位"。
 *
 * 因此 B 在开发环境补一份 9 条的演示池：仍然只用契约 4.3 的字段，不造新字段；
 * 只服务于"3 条一屏、可轮换、可补位"的交互验收。
 * 生产构建（import.meta.env.DEV=false）或关掉 Mock 时一律走真实接口，这份数据不会出现。
 *
 * id 约定：901~903 与前端 A 的 Mock 一致（CREATE_TASK，接受接口能命中）；
 * 904~909 是纯跳转型建议（OPEN_CHAT / OPEN_RESOURCE），只演示换一批与补位，不建任务。
 */

import type { Recommendation } from '../insight/types'

const GENERATED_AT = '2026-11-28T09:00:00+08:00'
const EXPIRES_AT = '2026-11-29T09:00:00+08:00'

const POOL: Recommendation[] = [
  {
    id: 901,
    scene: 'CHAT',
    type: 'KNOWLEDGE_POINT',
    title: '贝叶斯公式：从结论反推原因',
    reason: '本周数学任务里全概率公式的错题集中在“完备事件组”判断，继续学贝叶斯公式可以形成知识串联。',
    actionType: 'CREATE_TASK',
    actionLabel: '加入今日任务',
    actionPath: null,
    score: 0.92,
    estimatedMinutes: 15,
    subject: '数学',
    knowledgePoint: '贝叶斯公式',
    status: 'ACTIVE',
    createdAt: GENERATED_AT,
    expiresAt: EXPIRES_AT,
  },
  {
    id: 902,
    scene: 'CHAT',
    type: 'REVIEW',
    title: '矩阵相似对角化：回看 2 道逾期题',
    reason: '这个知识点有 2 项任务已逾期，先补完再进入特征值应用，避免后面推导断档。',
    actionType: 'CREATE_TASK',
    actionLabel: '加入今日任务',
    actionPath: null,
    score: 0.86,
    estimatedMinutes: 25,
    subject: '数学',
    knowledgePoint: '矩阵相似对角化',
    status: 'ACTIVE',
    createdAt: GENERATED_AT,
    expiresAt: EXPIRES_AT,
  },
  {
    id: 903,
    scene: 'CHAT',
    type: 'STUDY_TASK',
    title: '英语阅读：限时 20 分钟一篇',
    reason: '近三天英语任务都按计划完成，阅读速度稳定，可以再加一篇保持手感。',
    actionType: 'OPEN_CHAT',
    actionLabel: '去问问导学助手',
    actionPath: '/chat',
    score: 0.74,
    estimatedMinutes: 20,
    subject: '英语',
    knowledgePoint: '阅读理解',
    status: 'ACTIVE',
    createdAt: GENERATED_AT,
    expiresAt: EXPIRES_AT,
  },
  {
    id: 904,
    scene: 'CHAT',
    type: 'KNOWLEDGE_POINT',
    title: '条件概率：先分清“已知”与“求”',
    reason: '刚才的答疑里你两次把 P(B|A) 和 P(A|B) 写反，建议就这个点做一次专项复盘。',
    actionType: 'OPEN_CHAT',
    actionLabel: '继续追问这个点',
    actionPath: '/chat',
    score: 0.72,
    estimatedMinutes: 10,
    subject: '数学',
    knowledgePoint: '条件概率',
    status: 'ACTIVE',
    createdAt: GENERATED_AT,
    expiresAt: EXPIRES_AT,
  },
  {
    id: 905,
    scene: 'CHAT',
    type: 'KNOWLEDGE_POINT',
    title: '秩与方程组解的结构',
    reason: '特征值问题常要回到“秩—解空间”判断，先把 r(A) 与 r(A|b) 的关系理顺。',
    actionType: 'OPEN_CHAT',
    actionLabel: '让助手举例讲解',
    actionPath: '/chat',
    score: 0.7,
    estimatedMinutes: 15,
    subject: '数学',
    knowledgePoint: '矩阵的秩',
    status: 'ACTIVE',
    createdAt: GENERATED_AT,
    expiresAt: EXPIRES_AT,
  },
  {
    id: 906,
    scene: 'CHAT',
    type: 'STUDY_TASK',
    title: '长难句：每天拆 3 句真题',
    reason: '英语阅读错的题大多卡在从句切分，按句子结构拆解比整篇刷题更省时间。',
    actionType: 'OPEN_CHAT',
    actionLabel: '去问问导学助手',
    actionPath: '/chat',
    score: 0.66,
    estimatedMinutes: 20,
    subject: '英语',
    knowledgePoint: '长难句分析',
    status: 'ACTIVE',
    createdAt: GENERATED_AT,
    expiresAt: EXPIRES_AT,
  },
  {
    id: 907,
    scene: 'CHAT',
    type: 'RESOURCE',
    title: '本周数学错题整理成 PDF',
    reason: '你最近三次提问都围绕线代，把错题按知识点归档，复习时可以直接按标签翻。',
    actionType: 'OPEN_RESOURCE',
    actionLabel: '打开文件中心',
    actionPath: '/files',
    score: 0.62,
    estimatedMinutes: 15,
    subject: '数学',
    knowledgePoint: '错题整理',
    status: 'ACTIVE',
    createdAt: GENERATED_AT,
    expiresAt: EXPIRES_AT,
  },
  {
    id: 908,
    scene: 'CHAT',
    type: 'RESOURCE',
    title: '线代公式速查表（特征值部分）',
    reason: '把特征值、相似对角化、二次型标准化的公式放一页，做题时少翻书。',
    actionType: 'OPEN_RESOURCE',
    actionLabel: '查看速查表',
    actionPath: '/files',
    score: 0.6,
    estimatedMinutes: 5,
    subject: '数学',
    knowledgePoint: '特征值与二次型',
    status: 'ACTIVE',
    createdAt: GENERATED_AT,
    expiresAt: EXPIRES_AT,
  },
  {
    id: 909,
    scene: 'CHAT',
    type: 'KNOWLEDGE_POINT',
    title: '马原：剩余价值的计算口径',
    reason: '政治错题里剩余价值率与利润率的换算反复出错，趁热把两个公式区分开。',
    actionType: 'OPEN_CHAT',
    actionLabel: '去问问导学助手',
    actionPath: '/chat',
    score: 0.55,
    estimatedMinutes: 10,
    subject: '政治',
    knowledgePoint: '剩余价值',
    status: 'ACTIVE',
    createdAt: GENERATED_AT,
    expiresAt: EXPIRES_AT,
  },
]

/** 每次返回新对象，避免组件改到共享常量 */
export function mockChatCoachPool(): Recommendation[] {
  return POOL.map((item) => ({ ...item }))
}
