// Development-only DOM checks. Requires linkedom; the app has no runtime dependency.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { parseHTML } = require('linkedom');
const html = fs.readFileSync(__dirname + '/index.html', 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];

function boot(storage = new Map()) {
  const { window } = parseHTML(html), document = window.document;
  let active = document.querySelector('h1');
  Object.defineProperty(document, 'activeElement', { get: () => active });
  window.HTMLElement.prototype.focus = function () { active = this; };
  const dialog = document.querySelector('#settings');
  dialog.showModal = function () { this.open = true; };
  dialog.close = function () { this.open = false; };
  // linkedom models DOM, not native form behaviour or visual layout.
  Object.defineProperty(window.HTMLSelectElement.prototype, 'value', {
    configurable: true,
    get() { return this.testValue ?? [...this.options].find(o => o.selected)?.value ?? this.options[0]?.value ?? ''; },
    set(value) { this.testValue = String(value); },
  });
  const timers = [], context = vm.createContext({
    document, Intl, Date,
    URL: { createObjectURL: () => 'blob:test-photo', revokeObjectURL() {} },
    localStorage: {
      getItem: key => storage.get(key) || null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key),
    },
    setInterval: (fn, ms) => { timers.push({ fn, ms }); return timers.length; },
    clearInterval() {}, setTimeout() {}, clearTimeout() {},
  });
  vm.runInContext(script, context);
  return { document, storage, context, timers, $: selector => document.querySelector(selector) };
}

const storage = new Map([['family-tv-stops', JSON.stringify([
  { name: 'Legacy private stop', code: '00001', services: ['10'] },
])]]);
let ui = boot(storage), $ = ui.$;
assert.equal($('.bus-container .bus'), null);
assert.match($('#data-credit').textContent, /LTA DataMall/);
assert.equal($('#data-credit a').getAttribute('href'), 'https://datamall.lta.gov.sg/content/datamall/en/SingaporeOpenDataLicence.html');
assert.equal(ui.document.querySelectorAll('main .neutral-tile').length, 6);
assert.equal(ui.document.querySelectorAll('main .tile svg').length, 0);
assert.equal(storage.has('family-tv-stops'), false);
assert.equal(ui.document.querySelectorAll('main [data-app]').length, 8);
assert.equal($('.bus-empty button').textContent, 'Add a bus stop');

$('#network-button').onclick();
assert.equal($('#settings').open, true);
assert.equal($('#network-panel').hidden, false);
assert.equal($('#settings-title').textContent, 'Network settings');
$('#open-network-settings').onclick();
assert.match($('#toast').textContent, /browser preview cannot open TV settings/);
$('#settings-back').onclick();
assert.equal($('#settings-menu').hidden, false);
$('[data-open-panel="favourites-panel"]').onclick();
assert.equal($('#favourites-panel').hidden, false);
assert.equal($('#preferences-panel').hidden, true);

// Reordering survives reopening; removing a home tile preserves All apps access.
$('#app-right-app-0').onclick();
assert.equal($('#favourites-apps').children[0].dataset.app, 'YouTube');
let placement = $('#app-location-app-0');
placement.value = 'hidden'; placement.onchange();
assert.equal($('#favourites-apps [data-app="Netflix"]'), null);
assert([...ui.document.querySelectorAll('#all-apps-list button')].some(b => b.textContent === 'Netflix'));
ui = boot(storage); $ = ui.$;
assert.equal($('#favourites-apps [data-app="Netflix"]'), null);
$('#reset-apps').onclick();
assert.equal($('#favourites-apps').children[0].dataset.app, 'Netflix');

// Moving an app to Playtime remains launchable and persists its placement.
placement = $('#app-location-app-0'); placement.value = 'playtime'; placement.onchange();
assert($('#playtime-apps [data-app="Netflix"]'));
$('#playtime-apps [data-app="Netflix"]').onclick();
assert.match($('#toast').textContent, /Netflix will open on the TV/);

$('#settings-button').onclick();
$('[data-open-panel="preferences-panel"]').onclick();
Object.defineProperty($('#photos'), 'files', { configurable: true, value: [{ name: 'sample-photo.jpg', type: 'image/jpeg' }] });
$('#photos').onchange();
assert.equal($('#photo-enabled').checked, true);
assert.match($('#backdrop').style.backgroundImage, /blob:test-photo/);
$('#photo-fit').value = 'contain'; $('#photo-fit').onchange();
assert.equal($('#backdrop').style.backgroundSize, 'contain');
$('#photo-interval').value = '300'; $('#photo-interval').onchange();
assert.equal(ui.timers.at(-1).ms, 300000);
$('#photo-enabled').checked = false; $('#photo-enabled').onchange();
assert.equal($('#backdrop').style.backgroundImage, '');
ui = boot(storage); $ = ui.$;
assert.equal($('#photo-interval').value, '300');
assert.equal($('#photo-fit').value, 'contain');

// User-entered bus configuration and reordering still work after settings refactor.
for (const [name, code] of [['Sample stop A', '00001'], ['Sample stop B', '00002']]) {
  $('#stop-name').value = name; $('#stop-code').value = code; $('#services').value = '10, 20';
  $('#save-stop').onclick();
}
assert.equal(ui.document.querySelectorAll('.bus-container .bus').length, 2);
$('#move-left-00002').onclick();
assert.equal($('.bus h2').textContent, 'Sample stop B');
ui = boot(storage); $ = ui.$;
assert.equal($('.bus h2').textContent, 'Sample stop B');
$('#bus-edit-00002').onclick();
assert.equal($('#preferences-panel').hidden, false);
assert.equal($('#stop-code').value, '00002');
assert.equal($('#network-panel').hidden, true);

$('#settings-button').onclick(); $('[data-open-panel="about-panel"]').onclick();
assert.equal($('#about-panel').hidden, false);
assert.match($('#wallpaper-info').textContent, /default background/);
console.log('Passed: empty defaults and legacy cleanup; network/menu navigation; app pinning, placement, order and persistence; photo options; bus editing/order; About.');
console.log('These are DOM checks. Native TV actions and visual layout require device/browser verification.');

// Capacity comes only from fresh LTA Load values, for both arrival columns.
for (const [load,label,state,color] of [
  ['SEA','Seats','seats','#4ade80'], ['SDA','Standing','standing','#fb923c'],
  ['SDD','','unknown',null], ['DD','','unknown',null], ['LSD','Limited','limited','#f87171'],
  ['unexpected','','unknown',null], ['', '', 'unknown',null], [null,'','unknown',null],
]) {
  ui.context.testLoad = load;
  vm.runInContext(`liveMode=true;liveData.set('00002',{
    updatedAt:new Date().toISOString(),services:[{ServiceNo:'10',
      NextBus:{EstimatedArrival:new Date(Date.now()+300000).toISOString(),Monitored:0,Load:testLoad},
      NextBus2:{EstimatedArrival:new Date(Date.now()+600000).toISOString(),Monitored:1,Load:testLoad},
      NextBus3:{EstimatedArrival:new Date(Date.now()+900000).toISOString(),Monitored:1,Load:testLoad}}]
  });render(true);`,ui.context);
  const cells = [...ui.document.querySelectorAll('.bus tbody tr:first-child td')].slice(1,4);
  assert.match(cells[0].textContent,/^~[45] min/);
  assert.match(cells[1].textContent,/^(9|10) min/);
  for (const cell of cells) {
    assert.equal(cell.querySelector('.occupancy')?.textContent || '',label);
    if (label) assert(cell.querySelector('.occupancy-'+state));
  }
  if (color) assert(html.includes('.occupancy-'+state+'{color:'+color+'}'));
}
vm.runInContext(`liveData.get('00002').services[0].NextBus.Load='LSD';
  liveData.get('00002').services[0].NextBus.EstimatedArrival='';render(true);`,ui.context);
assert.equal($('.bus tbody tr td:nth-child(2)').textContent,'—');
assert.equal($('.bus .occupancy'),null);
vm.runInContext(`liveData.get('00002').updatedAt=new Date(Date.now()-61000).toISOString();render(true);`,ui.context);
assert.equal($('.bus .occupancy'),null);
assert.equal($('.bus tbody tr td:nth-child(3)').textContent,'—');
vm.runInContext('liveMode=false;render(true);',ui.context);
assert.equal($('.bus .occupancy'),null);
console.log('Passed: LTA occupancy labels/colors in both columns; unknown, missing, expired, stale and sample arrivals.');

// Each upcoming bus has its own Load; Type must never supply occupancy.
vm.runInContext(`liveMode=true;const data=liveData.get('00002');
 data.updatedAt=new Date().toISOString();
 ['NextBus','NextBus2','NextBus3'].forEach((key,i)=>{data.services[0][key]={
 EstimatedArrival:new Date(Date.now()+300000).toISOString(),Monitored:1,
 Load:['SEA','SDA','LSD'][i],Type:'DD'};});render(true);`,ui.context);
assert.deepEqual([...ui.document.querySelectorAll('.bus:first-child .occupancy')].map(n=>n.textContent),['Seats','Standing','Limited']);
vm.runInContext(`delete liveData.get('00002').services[0].NextBus.Load;render(true);`,ui.context);
assert.equal($('.bus tbody tr td:nth-child(2) .occupancy'),null);
assert.equal($('.bus tbody tr td:nth-child(3) .occupancy').textContent,'Standing');
assert.equal($('h1').textContent,'bay65');
assert.equal($('h1').getAttribute('aria-label'),'BAY65');
assert.match($('title').textContent,/BAY65/);
console.log('Passed: BAY65 branding and independent NextBus/NextBus2/NextBus3 capacity; vehicle Type ignored.');

// About must explain preview storage and credential handling without leaving the app.
$('#settings-button').onclick();
$('[data-open-panel="about-panel"]').onclick();
assert.equal($('#about-panel').hidden,false);
assert($('#preview-privacy'),'About must provide preview privacy information');
assert.match($('#preview-privacy').textContent,/saved in this browser/);
assert.match($('#preview-privacy').textContent,/photos.*not uploaded/i);
assert.match($('#preview-privacy').textContent,/key.*server memory/);
assert.match($('#preview-privacy').textContent,/No analytics or tracking/);
console.log('Passed: About exposes preview storage, photo, credential and tracking privacy information.');

// Capacity remains accessible while the home screen shows only colored times.
const labelledArrival=$('.bus tbody tr td:nth-child(3)');
assert.match(labelledArrival.getAttribute('aria-label'),/Standing/);
assert.equal(labelledArrival.querySelector('.occupancy').getAttribute('aria-hidden'),'true');
assert.equal(ui.document.querySelector('main .capacity-legend'),null);
assert($('#about-panel .capacity-legend'));
assert.match(html,/\.occupancy\{[^}]*clip-path:inset\(50%\)/);
console.log('Passed: color-only arrivals retain accessible capacity descriptions; legend is in About.');

assert.match(html,/#settings\{scrollbar-width:none\}/);
assert.match(html,/#settings::-webkit-scrollbar\{display:none\}/);
