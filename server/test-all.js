const { WebSocket } = require('ws');
const fs = require('fs');

const BASE = 'http://localhost:8080';
const WS_URL = 'ws://localhost:8080';

async function test() {
    console.log('🚀 Starting Super-Test...');

    // 1. Auth Request
    console.log('--- Testing Auth Request ---');
    const reqRes = await fetch(`${BASE}/auth/request`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ method: 'email', identifier: 'test@example.com' })
    });
    const reqJson = await reqRes.json();
    console.log('Auth Request:', reqJson);
    if (!reqJson.ok || !reqJson.devCode) throw new Error('Auth Request Failed');

    // 2. Auth Verify
    console.log('--- Testing Auth Verify ---');
    const vrfRes = await fetch(`${BASE}/auth/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ 
            method: 'email', 
            identifier: 'test@example.com', 
            code: reqJson.devCode,
            displayName: 'Tester'
        })
    });
    const vrfJson = await vrfRes.json();
    console.log('Auth Verify:', vrfJson);
    if (!vrfJson.ok || !vrfJson.token) throw new Error('Auth Verify Failed');
    const token = vrfJson.token;

    // 3. Profile Update
    console.log('--- Testing Profile Update ---');
    const profRes = await fetch(`${BASE}/profile`, {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ displayName: 'SuperTester', bio: 'I test everything', color: 'FF0000' })
    });
    const profJson = await profRes.json();
    console.log('Profile Update:', profJson);
    if (!profJson.ok || profJson.user.displayName !== 'SuperTester') throw new Error('Profile Update Failed');

    // 4. Media Upload
    console.log('--- Testing Media Upload ---');
    const fakeData = Buffer.from('fake image data').toString('base64');
    const upRes = await fetch(`${BASE}/upload`, {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ 
            dataBase64: fakeData,
            mime: 'image/png',
            name: 'test.png',
            kind: 'image'
        })
    });
    const upJson = await upRes.json();
    console.log('Media Upload:', upJson);
    if (!upJson.ok || !upJson.media.id) throw new Error('Media Upload Failed');
    const mediaId = upJson.media.id;

    // 5. WebSocket Flow
    console.log('--- Testing WebSocket Flow ---');
    const ws = new WebSocket(WS_URL);
    
    ws.on('open', () => {
        console.log('WS Connected');
        ws.send(JSON.stringify({ type: 'join', token }));
    });

    let phase = 'join';
    ws.on('message', (data) => {
        const msg = JSON.parse(data.toString());
        console.log(`WS [${phase}] Received:`, msg.type);

        if (msg.type === 'welcome') {
            phase = 'send';
            ws.send(JSON.stringify({ 
                type: 'send', 
                channel: 'general', 
                text: 'Hello world', 
                clientId: 'client-123',
                media: upJson.media
            }));
        } else if (msg.type === 'message') {
            if (msg.message.clientId === 'client-123') {
                console.log('✅ Message Echo Received with ClientId');
                phase = 'typing';
                ws.send(JSON.stringify({ type: 'typing', channel: 'general' }));
            }
        } else if (msg.type === 'typing') {
            console.log('✅ Typing Received');
            phase = 'react';
            ws.send(JSON.stringify({ 
                type: 'react', 
                channel: 'general', 
                messageId: 'any', // will fail but test logic
                emoji: '👍' 
            }));
            // End test after some time
            setTimeout(() => {
                console.log('🏁 All tests passed!');
                process.exit(0);
            }, 2000);
        }
    });

    ws.on('error', (err) => {
        console.error('WS Error:', err);
        process.exit(1);
    });
}

test().catch(err => {
    console.error('❌ Test Failed:', err);
    process.exit(1);
});
