const dbName = "zhicore";
const postIds = [
  "189000000000000101",
  "189000000000000102",
  "189000000000000103",
];

const database = db.getSiblingDB(dbName);

database.posts.deleteMany({ postId: { $in: postIds } });
database.post_contents.deleteMany({
  postId: {
    $in: [
      NumberLong("189000000000000101"),
      NumberLong("189000000000000102"),
      NumberLong("189000000000000103"),
    ],
  },
});

database.posts.insertMany([
  {
    postId: "189000000000000101",
    title: "ZhiCore 后端联调文章 A",
    content: [
      "# ZhiCore 后端联调文章 A",
      "",
      "这篇文章用于验证以下链路：",
      "",
      "- 用户角色与关注关系",
      "- 文章发布、标签、点赞与收藏",
      "- 评论主楼与回复",
      "- MongoDB 详情读取",
      "",
      "如果你在接口层看到这篇文章，说明 PostgreSQL 和 MongoDB 的最小联调数据已经对齐。",
    ].join("\n"),
    excerpt: "这是一篇用于验证用户、内容、评论和互动链路的固定已发布文章。",
    authorId: "189000000000000002",
    authorName: "测试作者",
    tags: [
      { id: "189000000000000201", name: "后端联调", slug: "seed-backend" },
      { id: "189000000000000202", name: "冒烟测试", slug: "seed-smoke" },
    ],
    status: "PUBLISHED",
    viewCount: 128,
    likeCount: 3,
    commentCount: 3,
    publishedAt: ISODate("2026-03-16T04:00:00.000Z"),
    createdAt: ISODate("2026-03-15T04:00:00.000Z"),
    updatedAt: ISODate("2026-03-18T04:00:00.000Z"),
    _class: "com.zhicore.content.infrastructure.persistence.mongo.document.PostDocument",
  },
  {
    postId: "189000000000000102",
    title: "ZhiCore 草稿联调文章 B",
    content: [
      "# ZhiCore 草稿联调文章 B",
      "",
      "这是一篇草稿文章，用于验证：",
      "",
      "- 内容已保存但尚未发布",
      "- 草稿列表与详情读取",
      "- PostgreSQL 与 MongoDB 内容一致性",
    ].join("\n"),
    excerpt: "这是一篇仅保存内容但未发布的草稿文章，用于验证草稿链路。",
    authorId: "189000000000000002",
    authorName: "测试作者",
    tags: [
      { id: "189000000000000202", name: "冒烟测试", slug: "seed-smoke" },
    ],
    status: "DRAFT",
    viewCount: 17,
    likeCount: 0,
    commentCount: 0,
    createdAt: ISODate("2026-03-18T08:00:00.000Z"),
    updatedAt: ISODate("2026-03-19T00:00:00.000Z"),
    _class: "com.zhicore.content.infrastructure.persistence.mongo.document.PostDocument",
  },
  {
    postId: "189000000000000103",
    title: "ZhiCore 社区互动演示文章 C",
    content: [
      "# ZhiCore 社区互动演示文章 C",
      "",
      "这篇文章用于验证审核员发文后的互动数据：",
      "",
      "- 点赞与收藏统计",
      "- 评论统计与详情展示",
      "- 标签读模型查询",
    ].join("\n"),
    excerpt: "这是一篇用于验证审核员发文、点赞、收藏和评论统计的文章。",
    authorId: "189000000000000003",
    authorName: "测试审核员",
    tags: [
      { id: "189000000000000201", name: "后端联调", slug: "seed-backend" },
      { id: "189000000000000203", name: "社区互动", slug: "seed-social" },
    ],
    status: "PUBLISHED",
    viewCount: 64,
    likeCount: 2,
    commentCount: 1,
    publishedAt: ISODate("2026-03-18T06:00:00.000Z"),
    createdAt: ISODate("2026-03-17T06:00:00.000Z"),
    updatedAt: ISODate("2026-03-19T02:00:00.000Z"),
    _class: "com.zhicore.content.infrastructure.persistence.mongo.document.PostDocument",
  },
]);

database.post_contents.insertMany([
  {
    postId: NumberLong("189000000000000101"),
    content: [
      "# ZhiCore 后端联调文章 A",
      "",
      "这篇文章用于验证 PostgreSQL 与 MongoDB 双写之后的详情查询。",
      "",
      "## 覆盖点",
      "",
      "- 用户角色",
      "- 关注关系",
      "- 文章互动",
      "- 评论回复",
      "- 私信会话",
    ].join("\n"),
    contentType: "markdown",
    createdAt: ISODate("2026-03-15T04:00:00.000Z"),
    updatedAt: ISODate("2026-03-18T04:00:00.000Z"),
    _class: "com.zhicore.content.infrastructure.persistence.mongo.document.PostContentDocument",
  },
  {
    postId: NumberLong("189000000000000102"),
    content: [
      "# ZhiCore 草稿联调文章 B",
      "",
      "这篇草稿用于测试内容已保存但未发布的接口行为。",
    ].join("\n"),
    contentType: "markdown",
    createdAt: ISODate("2026-03-18T08:00:00.000Z"),
    updatedAt: ISODate("2026-03-19T00:00:00.000Z"),
    _class: "com.zhicore.content.infrastructure.persistence.mongo.document.PostContentDocument",
  },
  {
    postId: NumberLong("189000000000000103"),
    content: [
      "# ZhiCore 社区互动演示文章 C",
      "",
      "这篇文章用于校验审核员发布内容后的互动统计是否正常回显。",
    ].join("\n"),
    contentType: "markdown",
    createdAt: ISODate("2026-03-17T06:00:00.000Z"),
    updatedAt: ISODate("2026-03-19T02:00:00.000Z"),
    _class: "com.zhicore.content.infrastructure.persistence.mongo.document.PostContentDocument",
  },
]);

printjson({
  db: dbName,
  postsSeeded: database.posts.countDocuments({ postId: { $in: postIds } }),
  postContentsSeeded: database.post_contents.countDocuments({
    postId: {
      $in: [
        NumberLong("189000000000000101"),
        NumberLong("189000000000000102"),
        NumberLong("189000000000000103"),
      ],
    },
  }),
});
