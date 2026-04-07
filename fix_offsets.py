import re

with open('app/src/main/cpp/nextkv/NextKV.cpp', 'r') as f:
    content = f.read()

# allocateSpace
content = content.replace('uint32_t requiredTotal = 6 + size;', 'uint32_t requiredTotal = 8 + size;')

# freeSpace
content = content.replace('m_freeBlocks[size].push_back(offset - 6);', 'm_freeBlocks[size].push_back(offset - 8);')

# writeKeyDefinition
content = content.replace('uint32_t sizeNeeded = 6 + 2 + keyBytes;', 'uint32_t sizeNeeded = 8 + 2 + keyBytes;')
content = content.replace('*(uint16_t*)(m_mmapPtr + offset + 6) = (uint16_t)keyBytes;', '*(uint16_t*)(m_mmapPtr + offset + 6) = 0;\n    *(uint16_t*)(m_mmapPtr + offset + 8) = (uint16_t)keyBytes;')
content = content.replace('memcpy(m_mmapPtr + offset + 8, key.data(), keyBytes);', 'memcpy(m_mmapPtr + offset + 10, key.data(), keyBytes);')

with open('app/src/main/cpp/nextkv/NextKV.cpp', 'w') as f:
    f.write(content)
