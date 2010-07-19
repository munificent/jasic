          MAXITERATIONS = 32
          y = 0 - (5 / 4)

yloop:    ' reset x
          x = 0 - 2
          line = ""
xloop:  
          iter = 0
          x0 = x
          y0 = y
          
          
iterloop: ' iterate the complex function p <- p^2 + seed
          ' square the complex number
          x1 = (x0 * x0) - (y0 * y0)
          y1 = 2 * x0 * y0
          
          ' add the seed
          x1 = x1 + x
          y1 = y1 + y
          
          x0 = x1
          y0 = y1
          
          ' stop if the point escaped
          d = (x0 * x0) + (y0 * y0)
          if d > 4 then enditer

          ' else, iterate
          iter = iter + 1
          
          ' if we've iterated enough, give up
          if iter = MAXITERATIONS then enditer
          
          goto iterloop
          
enditer:  ' figure out what "color" to draw the pixel
          if iter < 2 then draw0
          if iter < 4 then draw1
          if iter < 8 then draw2
          if iter < 14 then draw3
          if iter < 20 then draw4
          if iter < 26 then draw5
          
          pixel = "@"
          goto draw
          
draw0:    pixel = " "
          goto draw
draw1:    pixel = "-"
          goto draw
draw2:    pixel = "+"
          goto draw
draw3:    pixel = "="
          goto draw
draw4:    pixel = "*"
          goto draw
draw5:    pixel = "@"
          goto draw
                    
draw:     ' draw the pixel
          line = line + pixel
          goto enddraw

enddraw:  ' move to the next x position
          x = x + (1 / 29)
          if x < (3 / 4) then xloop
        
          ' print the completed line
          print line
          
          ' move to the next y position
          y = y + (1 / 11)
          if y < (5 / 4) then yloop