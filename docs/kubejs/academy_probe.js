// AcademyCraft KubeJS integration probe (used by KubeJSIntegrationGameTests).
AcademyEvents.skillCast('arc_gen', event => {
  event.cancel()
})

AcademyEvents.skillDamage(event => {
  if (event.attacker && event.attacker.tags.contains('academy_probe_double')) {
    event.amount = event.amount * 2
  }
})

ServerEvents.recipes(event => {
  event.recipes.academy.metal_forming('minecraft:diamond', 'minecraft:dirt', 'plate').time(5).energy(2).id('academy:kubejs_probe_forming')
  event.recipes.academy.imag_fusor('minecraft:emerald', 'minecraft:cobblestone', 1000).id('academy:kubejs_probe_fusor')
})

ServerEvents.loaded(event => {
  console.info('ACADEMY_KUBEJS_PROBE categories=' + Academy.categories() + ' supported=' + Academy.supportedMobSkills().size())
})
