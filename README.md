# What if every aircraft had a twitter account?

...Twitter would ban them.

So I'll probably use ActivityPub (Mastodon, Pleroma, etc.).

I think this is all you need to do:

```
npm install
npx shadow-cljs compile script
node out/script.js
```

To run a server that watches, recompiles, and runs tests:

```
npx shadow-cljs watch script test
```

- [ ] Get live aircraft info.
  - [x] Get data from local VRS.
  - [ ] Get data from asbexchange.com.
  - [ ] Get track screenshot.
- [ ] Get aircraft profile info.
  - [ ] Get photos.
    - [x] Get photos from airport-data.com.
    - [ ] Get other photos.
    - [ ] Get historical tracks.
- [ ] Create mastodon account.
  - [x] Create account.
  - [x] Add bio.
  - [x] Add avatar.
  - [x] Add banner.
  - [ ] Save account in database.

