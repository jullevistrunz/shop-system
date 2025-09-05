# Shop System for Fabric 1.21.7

A simple shop system for SMPs

## Installation

1. Download the latest version from https://github.com/jullevistrunz/shop-system/releases
2. Drag the ZIP into `<server folder>/mods`

## Usage

- `/shop myCredits`: display amount of own credits
- `/shop credits`: display amount of all player's credits
- `/shop pay <recipient> <amount>`: pay the given recipient a set amount
- `/shop sign <pos> <price> <stackSize>`: create a shop sign

## Creating a shop

1. Place down a chest and a sign in front of that chest.
2. Put the items you want to sell into that chest. All items must be of the same type, and have the same components (e.g. shulker boxes must be filled with the exact sime item stacks)
3. Look at the sign and run the `/shop sign <pos> <price> <stackSize>` command. `<pos>` is the position of the sign (you can autofill the coordinates using tab when looking at it). `<price>` is the amount of credits a customer has to pay at a time. `<stackSize>` is the amount of items sold at a time.
4. Customers can now buy items from your shop by right-clicking the sign.

## Earning credits

- Sell items using the shop system
- Hourly: $20
