# MongoDB Initialization Scripts

This directory contains initialization scripts for MongoDB used by the zhicore-content service.

## Overview

The MongoDB instance is used to store:
- **Post Content**: Article content (Markdown, HTML, plain text)
- **Post Versions**: Version history for articles
- **Post Drafts**: Auto-saved drafts
- **Post Archives**: Archived article content

## Collections

### 1. post_contents
Stores the main content of articles.

**Indexes:**
- `postId` (unique): Primary lookup key
- `updatedAt`: For sorting by update time
- `contentType`: For filtering by content type

### 2. post_versions
Stores version history of articles.

**Indexes:**
- `postId + version`: Composite index for version lookup
- `postId + editedAt`: For chronological version listing
- `editedBy + editedAt`: For user's edit history

### 3. post_drafts
Stores auto-saved drafts (one per user per post).

**Indexes:**
- `postId + userId` (unique): Ensures one draft per user per post
- `userId + savedAt`: For user's draft listing
- `savedAt` (TTL): Auto-deletes drafts after 30 days

### 4. post_archives
Stores archived article content.

**Indexes:**
- `postId` (unique): Primary lookup key
- `archivedAt`: For sorting by archive time
- `archiveReason`: For filtering by archive reason

## Initialization Script

The `init-mongo.js` script:
1. Creates the `zhicore` database
2. Creates all required collections
3. Creates indexes for optimal query performance
4. Inserts a sample document for verification

## MongoDB Version

- **Version**: 8.0.x
- **Support Until**: October 31, 2029
- **Performance**: 32% faster reads, 59% faster updates compared to 7.0

## Connection Details

Default connection parameters (can be overridden via environment variables):

```
Host: zhicore-mongodb
Port: 27017
Database: zhicore
Username: admin
Password: mongo123456
Auth Database: admin
```

## Mongo Express

A web-based MongoDB admin interface is available at:
- **URL**: http://localhost:8081
- **Username**: admin
- **Password**: express123456

## Verification

After starting the containers, verify the setup:

1. Check MongoDB logs:
   ```bash
   docker logs zhicore-mongodb
   ```

2. Access Mongo Express:
   ```
   http://localhost:8091
   ```

3. Check application health:
   ```bash
   curl http://localhost:8102/actuator/health
   ```

## Troubleshooting

### Connection Issues

If the application cannot connect to MongoDB:

1. Verify MongoDB is running:
   ```bash
   docker ps | grep zhicore-mongodb
   ```

2. Check MongoDB health:
   ```bash
   docker exec zhicore-mongodb mongosh --eval "db.adminCommand('ping')"
   ```

3. Verify credentials in `.env` file

### Index Issues

If indexes are not created:

1. Connect to MongoDB:
   ```bash
   docker exec -it zhicore-mongodb mongosh -u admin -p mongo123456 --authenticationDatabase admin
   ```

2. Switch to zhicore database:
   ```javascript
   use zhicore
   ```

3. List indexes:
   ```javascript
   db.post_contents.getIndexes()
   ```

4. Manually create indexes if needed (see init-mongo.js)

## Performance Tuning

For production environments, consider:

1. **Connection Pool**: Adjust `maxConnectionPoolSize` based on load
2. **Timeouts**: Tune `connectionTimeout` and `socketTimeout`
3. **Indexes**: Monitor slow queries and add indexes as needed
4. **Sharding**: Consider sharding for very large datasets

## Security

In production:

1. Use strong passwords
2. Enable TLS/SSL
3. Restrict network access
4. Enable audit logging
5. Regular backups
