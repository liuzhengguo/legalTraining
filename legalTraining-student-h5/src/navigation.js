// 学生端底部导航配置，首页与学习页共用，保证入口和顺序一致。
export const navigation = [
  { to: '/home', title: '首页', icon: 'home-o', routes: ['home'] },
  { to: '/course', title: '我的课程', icon: 'records', routes: ['courses', 'course', 'task'] },
  { to: '/score', title: '我的成绩', icon: 'chart-trending-o', routes: ['score'] },
  { to: '/achievements', title: '我的成果', icon: 'award-o', routes: ['achievements'] },
  { to: '/profile', title: '我的', icon: 'user-o', routes: ['profile', 'notifications', 'feedback', 'version', 'avatar'] }
]
