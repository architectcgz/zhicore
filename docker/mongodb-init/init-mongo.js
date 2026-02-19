// MongoDB Initialization Script for Blog Post Service
// This script creates the blog database, collections, and indexes

// Switch to blog database
db = db.getSiblingDB('blog');

// Create collections
db.createCollection('post_contents');
db.createCollection('post_versions');
db.createCollection('post_drafts');
db.createCollection('post_archives');

print('Collections created successfully');

// Create indexes for post_contents collection
db.post_contents.createIndex({ postId: 1 }, { unique: true, name: 'idx_postId_unique' });
db.post_contents.createIndex({ updatedAt: -1 }, { name: 'idx_updatedAt' });
db.post_contents.createIndex({ contentType: 1 }, { name: 'idx_contentType' });

print('Indexes created for post_contents collection');

// Create indexes for post_versions collection
db.post_versions.createIndex({ postId: 1, version: -1 }, { name: 'idx_postId_version' });
db.post_versions.createIndex({ postId: 1, editedAt: -1 }, { name: 'idx_postId_editedAt' });
db.post_versions.createIndex({ editedBy: 1, editedAt: -1 }, { name: 'idx_editedBy_editedAt' });

print('Indexes created for post_versions collection');

// Create indexes for post_drafts collection
// Unique compound index ensures one draft per user per post
db.post_drafts.createIndex(
    { postId: 1, userId: 1 }, 
    { unique: true, name: 'idx_postId_userId_unique' }
);
db.post_drafts.createIndex({ userId: 1, savedAt: -1 }, { name: 'idx_userId_savedAt' });
// TTL index: auto-delete drafts after 30 days (2592000 seconds)
db.post_drafts.createIndex(
    { savedAt: 1 }, 
    { expireAfterSeconds: 2592000, name: 'idx_savedAt_ttl' }
);

print('Indexes created for post_drafts collection');

// Create indexes for post_archives collection
db.post_archives.createIndex({ postId: 1 }, { unique: true, name: 'idx_postId_unique' });
db.post_archives.createIndex({ archivedAt: -1 }, { name: 'idx_archivedAt' });
db.post_archives.createIndex({ archiveReason: 1 }, { name: 'idx_archiveReason' });

print('Indexes created for post_archives collection');

// Insert sample document to verify schema (will be removed in production)
db.post_contents.insertOne({
    postId: 'sample-post-id',
    contentType: 'markdown',
    raw: '# Sample Post\n\nThis is a sample post for testing.',
    html: '<h1>Sample Post</h1><p>This is a sample post for testing.</p>',
    text: 'Sample Post This is a sample post for testing.',
    wordCount: 10,
    readingTime: 1,
    createdAt: new Date(),
    updatedAt: new Date()
});

print('Sample document inserted for verification');

// Verify collections and indexes
print('\n=== Collections ===');
db.getCollectionNames().forEach(function(collection) {
    print('- ' + collection);
});

print('\n=== Indexes ===');
['post_contents', 'post_versions', 'post_drafts', 'post_archives'].forEach(function(collection) {
    print('\n' + collection + ':');
    db.getCollection(collection).getIndexes().forEach(function(index) {
        print('  - ' + index.name + ': ' + JSON.stringify(index.key));
    });
});

print('\n=== MongoDB Initialization Complete ===');
